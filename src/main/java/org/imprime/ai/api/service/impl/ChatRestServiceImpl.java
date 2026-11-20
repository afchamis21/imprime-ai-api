package org.imprime.ai.api.service.impl;

import lombok.RequiredArgsConstructor;
import org.imprime.ai.api.http.ServiceContext;
import org.imprime.ai.api.http.request.chat.OfferDecisionRequest;
import org.imprime.ai.api.http.response.PageResponse;
import org.imprime.ai.api.http.response.chat.ChatDetailResponse;
import org.imprime.ai.api.http.response.chat.ChatSummaryResponse;
import org.imprime.ai.api.http.response.common.MakerBriefResponse;
import org.imprime.ai.api.model.Chat;
import org.imprime.ai.api.model.ChatMessage;
import org.imprime.ai.api.model.Company;
import org.imprime.ai.api.model.FileAsset;
import org.imprime.ai.api.model.Order;
import org.imprime.ai.api.model.User;
import org.imprime.ai.api.model.dto.MessageDTO;
import org.imprime.ai.api.model.dto.UserDTO;
import org.imprime.ai.api.model.enums.ChatRoleFilter;
import org.imprime.ai.api.model.enums.MessageType;
import org.imprime.ai.api.model.enums.NegotiationStatus;
import org.imprime.ai.api.model.enums.OrderStatusCd;
import org.imprime.ai.api.model.enums.StatusCd;
import org.imprime.ai.api.repo.db.ChatMessageRepository;
import org.imprime.ai.api.repo.db.ChatRepository;
import org.imprime.ai.api.repo.db.CompanyRepository;
import org.imprime.ai.api.repo.db.FileAssetRepository;
import org.imprime.ai.api.repo.db.OrderRepository;
import org.imprime.ai.api.service.ChatRestService;
import org.imprime.ai.api.service.ChatService;
import org.imprime.ai.api.service.OrderStatusService;
import org.imprime.ai.api.service.OrderViewAssembler;
import org.imprime.ai.api.service.UserService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ChatRestServiceImpl implements ChatRestService {
    private static final String DEFAULT_PROJECT_TITLE = "Impressão 3D sob demanda";

    private final ChatService chatService;
    private final ChatRepository chatRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final OrderRepository orderRepository;
    private final OrderStatusService orderStatusService;
    private final UserService userService;
    private final CompanyRepository companyRepository;
    private final FileAssetRepository fileAssetRepository;
    private final OrderViewAssembler orderViewAssembler;

    @Override
    public PageResponse<ChatSummaryResponse> listChats(ChatRoleFilter role, String orderGuid, NegotiationStatus status, Pageable pageable) {
        User caller = ServiceContext.getUserOrThrow();
        String callerCompanyGuid = caller.getCompanyId() != null
                ? companyRepository.findById(caller.getCompanyId()).map(Company::getGuid).orElse(null)
                : null;

        List<Chat> candidates;
        if (ChatRoleFilter.BUYER.equals(role)) {
            candidates = chatRepository.findAllByClientGuidAndStatusOrderByLastMessageAtDesc(caller.getGuid(), StatusCd.ACTIVE);
        } else if (ChatRoleFilter.MAKER.equals(role)) {
            candidates = callerCompanyGuid != null
                    ? chatRepository.findAllByCompanyGuidAndStatusOrderByLastMessageAtDesc(callerCompanyGuid, StatusCd.ACTIVE)
                    : List.of();
        } else {
            candidates = callerCompanyGuid != null
                    ? chatRepository.findAllByClientGuidOrCompanyGuidOrderByLastMessageAtDesc(caller.getGuid(), callerCompanyGuid)
                    : chatRepository.findAllByClientGuidAndStatusOrderByLastMessageAtDesc(caller.getGuid(), StatusCd.ACTIVE);
        }

        List<ChatSummaryResponse> filtered = candidates.stream()
                .filter(chat -> !StatusCd.DELETED.equals(chat.getStatus()))
                .filter(chat -> orderGuid == null || orderGuid.isBlank() || orderGuid.equals(chat.getOrderGuid()))
                .map(this::toSummaryResponse)
                .filter(summary -> status == null || status.equals(summary.status()))
                .toList();

        int start = (int) pageable.getOffset();
        int end = Math.min(start + pageable.getPageSize(), filtered.size());
        List<ChatSummaryResponse> slice = start >= filtered.size() ? List.of() : filtered.subList(start, end);

        Page<ChatSummaryResponse> page = new PageImpl<>(slice, pageable, filtered.size());
        return PageResponse.from(page);
    }

    @Override
    public ChatDetailResponse getChatDetail(UUID chatId) {
        Chat chat = chatService.findAuthorizedChat(chatId);

        Optional<Order> orderOpt = chat.getOrderGuid() != null
                ? orderRepository.findOrderByGuid(chat.getOrderGuid())
                : Optional.empty();
        OrderStatusCd orderStatus = orderOpt.map(o -> orderStatusService.findLatest(o).getStatusCd()).orElse(null);

        Optional<ChatMessage> latestOffer = chatMessageRepository.findTopByChatIdAndTypeOrderByCreateDtDesc(chatId, MessageType.OFFER);
        Optional<ChatMessage> lastMessage = chatMessageRepository.findTopByChatIdOrderByCreateDtDesc(chatId);

        BigDecimal quotedPrice = orderOpt
                .map(Order::getOrderTotal)
                .orElseGet(() -> latestOffer.map(this::parseNumericOffer).orElse(null));

        NegotiationStatus negotiationStatus = resolveNegotiationStatus(chat, orderStatus, latestOffer.isPresent(), lastMessage.isPresent());
        String projectTitle = resolveProjectTitle(orderOpt.orElse(null));
        UserDTO client = resolveUserDto(chat.getClientGuid());
        MakerBriefResponse maker = resolveMakerBrief(chat.getCompanyGuid());

        List<ChatMessage> recentDesc = chatMessageRepository.findTop20ByChatIdOrderByCreateDtDesc(chatId);
        List<MessageDTO> recentChronological = new java.util.ArrayList<>(recentDesc.stream()
                .map(this::toMessageDto)
                .toList());
        Collections.reverse(recentChronological);

        return new ChatDetailResponse(
                chat.getChatId(),
                chat.getOrderGuid(),
                orderViewAssembler.formatOrderCode(chat.getOrderGuid()),
                projectTitle,
                quotedPrice,
                orderStatus,
                negotiationStatus,
                0,
                client,
                maker,
                recentChronological,
                chat.getCreateDt(),
                chat.getLastMessageAt()
        );
    }

    @Override
    public PageResponse<MessageDTO> listChatMessages(UUID chatId, Pageable pageable) {
        chatService.findAuthorizedChat(chatId);
        Page<ChatMessage> page = chatMessageRepository.findAllByChatIdOrderByCreateDtDesc(chatId, pageable);
        return PageResponse.from(page.map(this::toMessageDto));
    }

    @Override
    public void markChatAsRead(UUID chatId) {
        chatService.findAuthorizedChat(chatId);
        // TODO Fase 2: persistir cursor de leitura por participante na tabela CHAT_PARTICIPANT_STATE
    }

    @Override
    public ChatDetailResponse decideOffer(UUID chatId, String messageGuid, OfferDecisionRequest request) {
        chatService.decideOfferByMessageGuid(chatId, messageGuid, Boolean.TRUE.equals(request.accept()), request.reason());
        return getChatDetail(chatId);
    }

    private ChatSummaryResponse toSummaryResponse(Chat chat) {
        Optional<Order> orderOpt = chat.getOrderGuid() != null
                ? orderRepository.findOrderByGuid(chat.getOrderGuid())
                : Optional.empty();
        OrderStatusCd orderStatus = orderOpt.map(o -> orderStatusService.findLatest(o).getStatusCd()).orElse(null);

        Optional<ChatMessage> latestOffer = chatMessageRepository.findTopByChatIdAndTypeOrderByCreateDtDesc(chat.getChatId(), MessageType.OFFER);
        Optional<ChatMessage> lastMessage = chatMessageRepository.findTopByChatIdOrderByCreateDtDesc(chat.getChatId());

        BigDecimal latestQuotedPrice = latestOffer.map(this::parseNumericOffer)
                .orElseGet(() -> orderOpt.map(Order::getOrderTotal).orElse(null));

        NegotiationStatus negotiationStatus = resolveNegotiationStatus(chat, orderStatus, latestOffer.isPresent(), lastMessage.isPresent());
        String projectTitle = resolveProjectTitle(orderOpt.orElse(null));

        return new ChatSummaryResponse(
                chat.getChatId(),
                chat.getOrderGuid(),
                orderViewAssembler.formatOrderCode(chat.getOrderGuid()),
                projectTitle,
                null,
                negotiationStatus,
                latestQuotedPrice,
                null,
                0,
                lastMessage.map(this::formatMessagePreview).orElse(null),
                chat.getLastMessageAt(),
                resolveUserDto(chat.getClientGuid()),
                resolveMakerBrief(chat.getCompanyGuid())
        );
    }

    private String formatMessagePreview(ChatMessage msg) {
        if (msg == null || msg.getContent() == null) {
            return null;
        }
        if (MessageType.OFFER.equals(msg.getType())) {
            BigDecimal val = parseNumericOffer(msg);
            if (val != null) {
                return String.format(java.util.Locale.forLanguageTag("pt-BR"), "Proposta: R$ %.2f", val);
            }
            return "Proposta: R$ " + msg.getContent();
        }
        if (MessageType.ATTACHMENT.equals(msg.getType())) {
            return "📎 Anexo enviado";
        }
        return msg.getContent();
    }

    private MessageDTO toMessageDto(ChatMessage msg) {
        User sender = msg.getSenderId() != null
                ? userService.findUserByGuid(msg.getSenderId()).orElse(null)
                : null;
        if (sender == null) {
            return new MessageDTO(msg.getGuid(), msg.getContent(), msg.getType(), null, msg.getCreateDt(), msg.getUpdateDt());
        }
        return MessageDTO.from(msg, sender);
    }

    private UserDTO resolveUserDto(String userGuid) {
        if (userGuid == null) {
            return null;
        }
        return userService.findUserByGuid(userGuid).map(UserDTO::from).orElse(null);
    }

    private MakerBriefResponse resolveMakerBrief(String companyGuid) {
        if (companyGuid == null) {
            return null;
        }
        return companyRepository.findByGuid(companyGuid).map(orderViewAssembler::toMakerBrief).orElse(null);
    }

    private String resolveProjectTitle(Order order) {
        if (order == null || order.getFileAssetId() == null) {
            return DEFAULT_PROJECT_TITLE;
        }
        return fileAssetRepository.findById(order.getFileAssetId())
                .map(FileAsset::getName)
                .orElse(DEFAULT_PROJECT_TITLE);
    }

    private NegotiationStatus resolveNegotiationStatus(
            Chat chat,
            OrderStatusCd orderStatus,
            boolean hasOffer,
            boolean hasMessages) {
        if (!StatusCd.ACTIVE.equals(chat.getStatus())) {
            return NegotiationStatus.CLOSED;
        }
        if (orderStatus != null) {
            switch (orderStatus) {
                case CANCELLED, ERROR, REFUNDED -> {
                    return NegotiationStatus.CANCELLED;
                }
                case COMPLETED -> {
                    return NegotiationStatus.CLOSED;
                }
                case IN_PRODUCTION, IN_TRANSIT -> {
                    return NegotiationStatus.IN_PRODUCTION;
                }
                case CONFIRMED -> {
                    return NegotiationStatus.APPROVED;
                }
                case CREATED, QUOTING -> {
                    // handled below
                }
            }
        }
        if (hasOffer) {
            return NegotiationStatus.QUOTE_RECEIVED;
        }
        return hasMessages ? NegotiationStatus.AWAITING_QUOTE : NegotiationStatus.OPEN;
    }

    private BigDecimal parseNumericOffer(ChatMessage offerMessage) {
        if (offerMessage == null || offerMessage.getContent() == null) {
            return null;
        }
        try {
            return new BigDecimal(offerMessage.getContent().trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
