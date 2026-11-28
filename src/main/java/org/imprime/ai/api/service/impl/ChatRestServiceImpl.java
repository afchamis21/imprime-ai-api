package org.imprime.ai.api.service.impl;

import jakarta.annotation.Nullable;
import lombok.RequiredArgsConstructor;
import org.imprime.ai.api.http.ServiceContext;
import org.imprime.ai.api.http.request.chat.CalculateChatOfferShippingRequest;
import org.imprime.ai.api.http.request.chat.CreateChatOfferRequest;
import org.imprime.ai.api.http.request.chat.OfferDecisionRequest;
import org.imprime.ai.api.http.request.chat.UpdateChatSellerNotesRequest;
import org.imprime.ai.api.http.response.PageResponse;
import org.imprime.ai.api.http.response.chat.ChatDetailResponse;
import org.imprime.ai.api.http.response.chat.ChatOfferShippingQuotesResponse;
import org.imprime.ai.api.http.response.chat.ChatSummaryResponse;
import org.imprime.ai.api.http.response.common.MakerBriefResponse;
import org.imprime.ai.api.model.Address;
import org.imprime.ai.api.model.Chat;
import org.imprime.ai.api.model.ChatMessage;
import org.imprime.ai.api.model.Company;
import org.imprime.ai.api.model.FileAsset;
import org.imprime.ai.api.model.Order;
import org.imprime.ai.api.model.User;
import org.imprime.ai.api.model.dto.AddressDTO;
import org.imprime.ai.api.model.dto.MessageDTO;
import org.imprime.ai.api.model.dto.UserDTO;
import org.imprime.ai.api.model.enums.ChatRoleFilter;
import org.imprime.ai.api.model.enums.EntityType;
import org.imprime.ai.api.model.enums.MessageCd;
import org.imprime.ai.api.model.enums.MessageType;
import org.imprime.ai.api.model.enums.NegotiationStatus;
import org.imprime.ai.api.model.enums.OrderStatusCd;
import org.imprime.ai.api.model.enums.StatusCd;
import org.imprime.ai.api.model.exception.BadRequestException;
import org.imprime.ai.api.model.exception.ConflictException;
import org.imprime.ai.api.model.exception.ForbiddenException;
import org.imprime.ai.api.model.exception.NotFoundException;
import org.imprime.ai.api.notification.chat.ChatReadStateService;
import org.imprime.ai.api.repo.db.AddressRepository;
import org.imprime.ai.api.repo.db.ChatMessageRepository;
import org.imprime.ai.api.repo.db.ChatRepository;
import org.imprime.ai.api.repo.db.CompanyRepository;
import org.imprime.ai.api.repo.db.FileAssetRepository;
import org.imprime.ai.api.repo.db.OrderRepository;
import org.imprime.ai.api.service.ChatRestService;
import org.imprime.ai.api.service.ChatService;
import org.imprime.ai.api.service.OrderStatusService;
import org.imprime.ai.api.service.OrderViewAssembler;
import org.imprime.ai.api.service.ShippingQuoteService;
import org.imprime.ai.api.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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
    @Autowired(required = false)
    private ChatReadStateService chatReadStateService;
    @Autowired(required = false)
    private ShippingQuoteService shippingQuoteService;
    @Autowired(required = false)
    private AddressRepository addressRepository;

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

        List<Chat> visibleChats = candidates.stream()
                .filter(chat -> !StatusCd.DELETED.equals(chat.getStatus()))
                .filter(chat -> orderGuid == null || orderGuid.isBlank() || orderGuid.equals(chat.getOrderGuid()))
                .toList();

        Map<UUID, Integer> unreadByChatId = (chatReadStateService != null && !visibleChats.isEmpty())
                ? chatReadStateService.countUnreadByChatIds(
                        visibleChats.stream().map(Chat::getChatId).toList(),
                        caller.getGuid()
                )
                : Map.of();

        List<ChatSummaryResponse> filtered = visibleChats.stream()
                .map(chat -> toSummaryResponse(chat, unreadByChatId.getOrDefault(chat.getChatId(), 0)))
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

        User caller = ServiceContext.getUser().orElse(null);
        boolean isCallerMaker = caller != null && isCallerMakerForChat(caller, chat);
        int unreadCount = (chatReadStateService != null && caller != null)
                ? chatReadStateService.countUnread(chatId, caller.getGuid())
                : 0;

        return new ChatDetailResponse(
                chat.getChatId(),
                chat.getOrderGuid(),
                orderViewAssembler.formatOrderCode(chat.getOrderGuid()),
                projectTitle,
                quotedPrice,
                orderStatus,
                negotiationStatus,
                unreadCount,
                client,
                maker,
                recentChronological,
                chat.getCreateDt(),
                chat.getLastMessageAt(),
                isCallerMaker ? chat.getSellerNotes() : null
        );
    }

    @Override
    public PageResponse<MessageDTO> listChatMessages(UUID chatId, Pageable pageable) {
        chatService.findAuthorizedChat(chatId);
        if (pageable != null && pageable.getPageNumber() == 0 && chatReadStateService != null) {
            ServiceContext.getUser().ifPresent(caller -> chatReadStateService.markAsRead(chatId, caller.getGuid()));
        }
        Page<ChatMessage> page = chatMessageRepository.findAllByChatIdOrderByCreateDtDesc(chatId, pageable);
        return PageResponse.from(page.map(this::toMessageDto));
    }

    @Override
    public void markChatAsRead(UUID chatId) {
        chatService.findAuthorizedChat(chatId);
        User caller = ServiceContext.getUserOrThrow();
        if (chatReadStateService != null) {
            chatReadStateService.markAsRead(chatId, caller.getGuid());
        }
    }

    @Override
    public MessageDTO createOffer(UUID chatId, CreateChatOfferRequest request) {
        return chatService.createOffer(chatId, request);
    }

    @Override
    public ChatOfferShippingQuotesResponse calculateOfferShippingQuotes(
            UUID chatId,
            String messageGuid,
            @Nullable CalculateChatOfferShippingRequest request
    ) {
        User caller = ServiceContext.getUserOrThrow();
        Chat chat = chatService.findAuthorizedChat(chatId);
        if (!Objects.equals(chat.getClientGuid(), caller.getGuid())) {
            throw new ForbiddenException();
        }
        if (!StatusCd.ACTIVE.equals(chat.getStatus())) {
            throw new ConflictException(MessageCd.INVALID_ORDER_STATUS_TRANSITION);
        }

        ChatMessage offerMessage = chatMessageRepository.findByGuidAndChatId(messageGuid, chatId)
                .orElseThrow(() -> new NotFoundException(MessageCd.CHAT_NOT_FOUND));
        if (!MessageType.OFFER.equals(offerMessage.getType())) {
            throw new BadRequestException(MessageCd.MESSAGE_NOT_OFFER);
        }
        BigDecimal serviceAmount = parseNumericOffer(offerMessage);
        if (serviceAmount == null) {
            throw new BadRequestException(MessageCd.OFFER_NOT_NUMERIC);
        }
        if (serviceAmount.signum() <= 0 || !ChatService.hasPackageShippingData(offerMessage)) {
            throw new BadRequestException(MessageCd.VALIDATION_ERROR);
        }
        serviceAmount = serviceAmount.setScale(2, RoundingMode.HALF_UP);

        Order order = chat.getOrderGuid() != null
                ? orderRepository.findOrderByGuid(chat.getOrderGuid())
                .orElseThrow(() -> new NotFoundException(MessageCd.ORDER_NOT_FOUND))
                : null;
        if (order == null) {
            throw new NotFoundException(MessageCd.ORDER_NOT_FOUND);
        }
        if (order.getPaymentConfirmedAt() != null) {
            throw new ConflictException(MessageCd.INVALID_ORDER_STATUS_TRANSITION);
        }
        OrderStatusCd orderStatus = orderStatusService.findLatest(order).getStatusCd();
        if (!OrderStatusCd.QUOTING.equals(orderStatus) && !OrderStatusCd.CONFIRMED.equals(orderStatus)) {
            throw new ConflictException(MessageCd.INVALID_ORDER_STATUS_TRANSITION);
        }

        Company company = companyRepository.findByGuid(chat.getCompanyGuid())
                .orElseThrow(() -> new NotFoundException(MessageCd.COMPANY_NOT_FOUND));
        String originZipCode = resolveCompanyOriginZipCode(company);

        String requestedAddressGuid = request != null && request.addressGuid() != null && !request.addressGuid().isBlank()
                ? request.addressGuid().trim()
                : null;
        Address destination = resolveBuyerDestinationAddress(caller, order, requestedAddressGuid);
        if (destination == null) {
            return new ChatOfferShippingQuotesResponse(
                    offerMessage.getGuid(),
                    chat.getOrderGuid(),
                    serviceAmount,
                    offerMessage.getProductionDays(),
                    offerMessage.getPackageWeight(),
                    offerMessage.getPackageHeight(),
                    offerMessage.getPackageWidth(),
                    offerMessage.getPackageLength(),
                    true,
                    null,
                    originZipCode,
                    List.of()
            );
        }

        if (shippingQuoteService == null) {
            throw new IllegalStateException("ShippingQuoteService indisponível.");
        }

        ShippingQuoteService.ChatOfferCalculationResult calc = shippingQuoteService.calculateForChatOffer(
                caller,
                company,
                destination,
                offerMessage,
                serviceAmount,
                resolveProjectTitle(order)
        );
        Integer productionDays = offerMessage.getProductionDays();
        final BigDecimal finalServiceAmount = serviceAmount;
        List<ChatOfferShippingQuotesResponse.ChatOfferShippingOption> options = calc.options().stream()
                .map(opt -> {
                    Integer carrierDays = opt.deliveryDays();
                    Integer totalDays = (productionDays != null && carrierDays != null)
                            ? productionDays + carrierDays
                            : carrierDays;
                    BigDecimal optionPrice = opt.price() != null
                            ? opt.price().setScale(2, RoundingMode.HALF_UP)
                            : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
                    return new ChatOfferShippingQuotesResponse.ChatOfferShippingOption(
                            opt.quoteGuid(),
                            opt.serviceId(),
                            opt.serviceName(),
                            opt.carrierName(),
                            optionPrice,
                            carrierDays,
                            totalDays,
                            finalServiceAmount.add(optionPrice).setScale(2, RoundingMode.HALF_UP)
                    );
                })
                .toList();

        return new ChatOfferShippingQuotesResponse(
                offerMessage.getGuid(),
                chat.getOrderGuid(),
                serviceAmount,
                productionDays,
                offerMessage.getPackageWeight(),
                offerMessage.getPackageHeight(),
                offerMessage.getPackageWidth(),
                offerMessage.getPackageLength(),
                false,
                AddressDTO.from(destination),
                calc.originZipCode(),
                options
        );
    }

    @Override
    public ChatDetailResponse decideOffer(UUID chatId, String messageGuid, OfferDecisionRequest request) {
        chatService.decideOfferByMessageGuid(
                chatId,
                messageGuid,
                Boolean.TRUE.equals(request.accept()),
                request.reason(),
                request.shippingQuoteGuid(),
                request.addressGuid()
        );
        return getChatDetail(chatId);
    }

    @Override
    public ChatDetailResponse updateSellerNotes(UUID chatId, UpdateChatSellerNotesRequest request) {
        User caller = ServiceContext.getUserOrThrow();
        Chat chat = chatService.findAuthorizedChat(chatId);
        if (!isCallerMakerForChat(caller, chat)) {
            throw new ForbiddenException();
        }

        String raw = request != null ? request.sellerNotes() : null;
        String normalized = null;
        if (raw != null && !raw.isBlank()) {
            String trimmed = raw.trim();
            normalized = trimmed.length() > 2000 ? trimmed.substring(0, 2000) : trimmed;
        }
        chat.setSellerNotes(normalized);
        chatRepository.save(chat);
        return getChatDetail(chatId);
    }

    private boolean isCallerMakerForChat(User caller, Chat chat) {
        if (caller == null || chat == null || caller.getCompanyId() == null || chat.getCompanyGuid() == null) {
            return false;
        }
        return companyRepository.findById(caller.getCompanyId())
                .map(Company::getGuid)
                .map(guid -> guid.equals(chat.getCompanyGuid()))
                .orElse(false);
    }

    private ChatSummaryResponse toSummaryResponse(Chat chat, int unreadCount) {
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
                unreadCount,
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
        return MessageDTO.from(msg, sender);
    }

    private @Nullable Address resolveBuyerDestinationAddress(User buyer, Order order, @Nullable String requestedAddressGuid) {
        if (addressRepository == null) {
            return null;
        }
        if (requestedAddressGuid != null) {
            Address addr = addressRepository.findByGuidAndStatus(requestedAddressGuid, StatusCd.ACTIVE)
                    .orElseThrow(() -> new BadRequestException(MessageCd.INVALID_ADDRESS_GUID));
            if (!EntityType.USER.equals(addr.getOwnerType()) || !Objects.equals(buyer.getId(), addr.getOwnerId())) {
                throw new ForbiddenException();
            }
            if (!hasValidZipCode(addr)) {
                throw new BadRequestException(MessageCd.INVALID_ADDRESS_ZIP_CODE);
            }
            return addr;
        }
        List<Address> personalAddresses = addressRepository.findAllByOwnerTypeAndOwnerIdAndStatus(
                EntityType.USER, buyer.getId(), StatusCd.ACTIVE);
        List<Address> validAddresses = personalAddresses.stream()
                .filter(this::hasValidZipCode)
                .toList();
        if (validAddresses.isEmpty()) {
            return null;
        }
        return validAddresses.stream()
                .filter(a -> Boolean.TRUE.equals(a.getDefaultAddress())
                        || Objects.equals(a.getId(), buyer.getPrimaryAddressId()))
                .findFirst()
                .or(() -> validAddresses.stream()
                        .filter(a -> Objects.equals(a.getId(), order.getToAddressId()))
                        .findFirst())
                .orElse(validAddresses.getFirst());
    }

    private @Nullable String resolveCompanyOriginZipCode(Company company) {
        if (company == null || addressRepository == null) {
            return null;
        }
        if (company.getAddressId() != null) {
            Address addr = addressRepository.findById(company.getAddressId()).orElse(null);
            if (addr != null && StatusCd.ACTIVE.equals(addr.getStatus())
                    && EntityType.COMPANY.equals(addr.getOwnerType())
                    && Objects.equals(company.getId(), addr.getOwnerId())
                    && hasValidZipCode(addr)) {
                return addr.getZipCode();
            }
        }
        return addressRepository.findAllByOwnerTypeAndOwnerIdAndStatus(EntityType.COMPANY, company.getId(), StatusCd.ACTIVE)
                .stream()
                .filter(this::hasValidZipCode)
                .map(Address::getZipCode)
                .findFirst()
                .orElse(null);
    }

    private boolean hasValidZipCode(@Nullable Address address) {
        if (address == null || address.getZipCode() == null) {
            return false;
        }
        String digits = address.getZipCode().replaceAll("\\D", "");
        return digits.length() == 8;
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
        if (orderStatus == OrderStatusCd.CANCELLED || orderStatus == OrderStatusCd.REFUNDED) {
            return NegotiationStatus.CANCELLED;
        }
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
