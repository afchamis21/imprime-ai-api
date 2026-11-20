package org.imprime.ai.api.service;

import jakarta.annotation.Nullable;
import jakarta.transaction.Transactional;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.imprime.ai.api.http.ServiceContext;
import org.imprime.ai.api.http.request.chat.QuoteDecisionRequest;
import org.imprime.ai.api.http.request.chat.SendMessageRequest;
import org.imprime.ai.api.http.request.chat.StartChatRequest;
import org.imprime.ai.api.model.*;
import org.imprime.ai.api.model.dto.MessageDTO;
import org.imprime.ai.api.model.enums.MessageCd;
import org.imprime.ai.api.model.enums.MessageType;
import org.imprime.ai.api.model.enums.OrderStatusCd;
import org.imprime.ai.api.model.enums.StatusCd;
import org.imprime.ai.api.model.exception.BadRequestException;
import org.imprime.ai.api.model.exception.ConflictException;
import org.imprime.ai.api.model.exception.ForbiddenException;
import org.imprime.ai.api.model.exception.NotFoundException;
import org.imprime.ai.api.repo.db.ChatMessageRepository;
import org.imprime.ai.api.repo.db.ChatRepository;
import org.imprime.ai.api.websocket.chat.ChatEvent;
import org.imprime.ai.api.websocket.chat.ChatEventToPublish;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatService {
    private final CompanyService companyService;
    private final OrderService orderService;
    private final OrderStatusService orderStatusService;

    private final ChatRepository chatRepository;
    private final ChatMessageRepository chatMessageRepository;

    private final ApplicationEventPublisher eventPublisher;
    private final UserService userService;

    @Transactional
    public void start(StartChatRequest request) {
        User caller = ServiceContext.getUserOrThrow();

        Optional<Order> optionalOrder = orderService.findByGuid(request.orderGuid());
        Order order = optionalOrder.orElseThrow(() -> new BadRequestException(MessageCd.ORDER_NOT_FOUND));

        if (!order.getBuyerId().equals(caller.getId())) {
            log.warn("User trying to start chat on Order [{}]", order.getId());
            throw new ForbiddenException();
        }

        OrderStatus latest = orderStatusService.findLatest(order);
        if (!OrderStatusCd.QUOTING.equals(latest.getStatusCd())) {
            log.info("Order [{}] is already in status [{}]; cloning a new QUOTING order for buyer [{}]",
                    order.getGuid(), latest.getStatusCd(), caller.getGuid());
            order = orderService.cloneOrderForNewQuote(order, caller);
        }

        Optional<Company> optionalCompany = companyService.findByCompanyGuid(request.makerGuid());
        Company company = optionalCompany.orElseThrow(() -> new BadRequestException(MessageCd.COMPANY_NOT_FOUND));

        Chat chat = chatRepository
                .findFirstByOrderGuidAndCompanyGuidAndStatusOrderByCreateDtDesc(
                        order.getGuid(), company.getGuid(), StatusCd.ACTIVE
                )
                .orElseGet(() -> {
                    Chat created = new Chat();
                    created.setOrderGuid(order.getGuid());
                    created.setClientGuid(caller.getGuid());
                    created.setCompanyGuid(company.getGuid());
                    created.setLastMessageAt(Instant.now());
                    return chatRepository.saveAndFlush(created);
                });

        sendMessage(chat, request.message(), getChatMembers(chat));
    }

    @Transactional
    public void sendMessage(UUID chatId, SendMessageRequest request) {
        Chat chat = findChat(chatId);

        sendMessage(chat, request);
    }

    @Transactional
    public void sendMessage(Chat chat, SendMessageRequest request) {
        sendMessage(chat, request, null);
    }

    @Transactional
    public void sendMessage(Chat chat, SendMessageRequest request, @Nullable List<String> recipients) {
        User caller = ServiceContext.getUserOrThrow();

        if (MessageType.OFFER.equals(request.type())) {
            try {
                Double.valueOf(request.content());
            } catch (NumberFormatException e) {
                log.warn("Offer Message sent but value is not numeric!");
                throw new BadRequestException(MessageCd.OFFER_NOT_NUMERIC);
            }
        }

        ChatMessage message = new ChatMessage();
        message.setChatId(chat.getChatId());
        message.setSenderId(caller.getGuid());
        message.setContent(request.content());
        message.setType(request.type());

        ChatMessage saved = chatMessageRepository.save(message);
        if (saved != null) {
            message = saved;
        }
        chatRepository.updateLastMessageSentAt(chat.getChatId());

        if (MessageType.SYSTEM.equals(request.type())
                && request.content() != null
                && request.content().toLowerCase().contains("pagamento")
                && request.content().toLowerCase().contains("produ")
                && chat.getOrderGuid() != null) {
            try {
                orderService.findByGuid(chat.getOrderGuid()).ifPresent(order -> {
                    OrderStatus latest = orderStatusService.findLatest(order);
                    if (latest != null && OrderStatusCd.CONFIRMED.equals(latest.getStatusCd())) {
                        orderStatusService.move(order, OrderStatusCd.IN_PRODUCTION);
                        log.info("Order [{}] moved to IN_PRODUCTION after payment confirmation in Chat [{}]",
                                order.getGuid(), chat.getChatId());
                    }
                });
            } catch (Exception e) {
                log.warn("Could not transition order [{}] to IN_PRODUCTION from chat payment event", chat.getOrderGuid(), e);
            }
        }

        if (recipients == null) {
            recipients = getChatMembers(chat);
        }

        deliverMessage(chat.getChatId(), MessageDTO.from(message, caller), recipients);
    }

    @Transactional
    public void handleQuoteDecision(UUID chatId, QuoteDecisionRequest request) {
        log.info("Incoming Quote Decision for Chat [{}]", chatId);
        if (request == null) {
            throw new BadRequestException(MessageCd.INVALID_REQUEST_BODY);
        }

        if (request.message() != null
                && request.message().messageGuid() != null
                && !request.message().messageGuid().isBlank()) {
            decideOfferByMessageGuid(chatId, request.message().messageGuid(), Boolean.TRUE.equals(request.accept()), null);
            return;
        }

        User caller = ServiceContext.getUserOrThrow();
        Chat chat = findChat(chatId);

        MessageDTO quoteMessage = request.message();
        if (quoteMessage == null || !MessageType.OFFER.equals(quoteMessage.type())) {
            log.warn("Quote Decision request received without Quote Message! [{}]", quoteMessage != null ? quoteMessage.type() : null);
            throw new BadRequestException(MessageCd.MESSAGE_NOT_OFFER);
        }

        if (quoteMessage.sender() != null
                && quoteMessage.sender().userGuid() != null
                && caller.getGuid().equals(quoteMessage.sender().userGuid())) {
            throw new ForbiddenException();
        }

        if (Boolean.TRUE.equals(request.accept())) {
            acceptOffer(caller, chat, quoteMessage);
        } else {
            declineOffer(caller, chat, quoteMessage);
        }
    }

    @Transactional
    public Chat decideOfferByMessageGuid(UUID chatId, String messageGuid, boolean accept, @Nullable String reason) {
        User caller = ServiceContext.getUserOrThrow();
        Chat chat = findChat(chatId);

        Optional<ChatMessage> dbMsgOpt = chatMessageRepository.findByGuidAndChatId(messageGuid, chatId);
        if (dbMsgOpt.isPresent()) {
            ChatMessage msg = dbMsgOpt.get();
            if (msg.getSenderId() != null && caller.getGuid().equals(msg.getSenderId())) {
                log.warn("User [{}] attempted to decide their own offer [{}] in Chat [{}]",
                        caller.getGuid(), messageGuid, chatId);
                throw new ForbiddenException();
            }
        } else if (!caller.getGuid().equals(chat.getClientGuid())) {
            log.warn("User [{}] attempted to decide offer in Chat [{}] where client is [{}]",
                    caller.getGuid(), chatId, chat.getClientGuid());
            throw new ForbiddenException();
        }

        if (!StatusCd.ACTIVE.equals(chat.getStatus())) {
            throw new ConflictException(MessageCd.INVALID_ORDER_STATUS_TRANSITION);
        }

        Order order = orderService.findByGuid(chat.getOrderGuid())
                .orElseThrow(() -> new NotFoundException(MessageCd.ORDER_NOT_FOUND));
        OrderStatus latestOrderStatus = orderStatusService.findLatest(order);
        if (!OrderStatusCd.QUOTING.equals(latestOrderStatus.getStatusCd())) {
            throw new ConflictException(MessageCd.INVALID_ORDER_STATUS_TRANSITION);
        }

        ChatMessage dbMsg = dbMsgOpt
                .orElseThrow(() -> new NotFoundException(MessageCd.CHAT_NOT_FOUND));

        if (!MessageType.OFFER.equals(dbMsg.getType())) {
            log.warn("Message [{}] in Chat [{}] is type [{}], expected OFFER", messageGuid, chatId, dbMsg.getType());
            throw new BadRequestException(MessageCd.MESSAGE_NOT_OFFER);
        }

        try {
            new BigDecimal(dbMsg.getContent().trim());
        } catch (Exception e) {
            throw new BadRequestException(MessageCd.OFFER_NOT_NUMERIC);
        }

        User sender = userService.findUserByGuid(dbMsg.getSenderId()).orElse(caller);
        MessageDTO authoritativeQuote = MessageDTO.from(dbMsg, sender);

        if (accept) {
            acceptOffer(caller, chat, authoritativeQuote);
        } else {
            declineOffer(caller, chat, authoritativeQuote, reason);
        }

        return chat;
    }

    @Transactional
    public void acceptOffer(User caller, Chat chat, MessageDTO quoteMessage) {
        log.info("Accepting offer for Chat [{}]", chat.getChatId());
        BigDecimal offer = new BigDecimal(quoteMessage.content().trim());

        Company company = companyService.findByCompanyGuid(chat.getCompanyGuid()).orElseThrow();

        OrderStatus orderStatus = orderService.acceptOffer(chat.getOrderGuid(), offer, company);
        log.info("Order [{}] Confirmed! New Status [{}]", orderStatus.getOrderId(), orderStatus.getStatusCd());

        String text = "Proposta de R$ %.2f aprovada por %s! Pedido confirmado.".formatted(
                offer,
                caller.getFullName()
        );
        sendMessage(chat, new SendMessageRequest(text, MessageType.SYSTEM), getChatMembers(chat));

        disableChatsForOrder(chat.getOrderGuid(), chat);
    }

    @Transactional
    public void disableChatsForOrder(String orderGuid, Chat selected) {
        List<Chat> chats = chatRepository.findAllByOrderGuid(orderGuid);

        List<Chat> disabledChats = chats.stream()
                .filter(chat -> !chat.getChatId().equals(selected.getChatId()))
                .peek(chat -> chat.setStatus(StatusCd.INACTIVE)).toList();
        
        // TODO this can be VERY MUCH improved
        disabledChats = chatRepository.saveAll(disabledChats);
        for (Chat chat : disabledChats) {
            List<String> members = getChatMembers(chat);
            sendMessage(chat, new SendMessageRequest("Chat desabilitado", MessageType.SYSTEM), members);
            eventPublisher.publishEvent(new ChatEventToPublish(members, ChatEvent.chatDisabled(chat.getChatId())));
        }
    }

    @Transactional
    public void declineOffer(User caller, Chat chat, MessageDTO quoteMessage) {
        declineOffer(caller, chat, quoteMessage, null);
    }

    @Transactional
    public void declineOffer(User caller, Chat chat, MessageDTO quoteMessage, @Nullable String reason) {
        log.info("Rejecting offer for Chat [{}]", chat.getChatId());

        String baseText = "Oferta de R$ %.2f recusada por %s".formatted(
                new BigDecimal(quoteMessage.content().trim()),
                caller.getFullName()
        );
        String text = (reason != null && !reason.isBlank()) ? baseText + " (" + reason.trim() + ")" : baseText;

        SendMessageRequest messageRequest = new SendMessageRequest(text, MessageType.SYSTEM);

        sendMessage(chat, messageRequest, getChatMembers(chat));
    }

    public @NonNull Chat findAuthorizedChat(UUID chatId) {
        return findChat(chatId);
    }

    private @NonNull Chat findChat(UUID chatId) {
        User caller = ServiceContext.getUserOrThrow();

        Chat chat = chatRepository.findById(chatId).orElseThrow(() -> new NotFoundException(MessageCd.CHAT_NOT_FOUND));

        List<String> members = getChatMembers(chat);
        boolean isMember = members.stream().anyMatch(guid -> guid.equals(caller.getGuid()));

        if (!isMember) {
            throw new ForbiddenException();
        }

        return chat;
    }

    private List<String> getChatMembers(Chat chat) {
        return chatRepository.findAllMemberGuid(chat.getChatId());
    }

    public void deliverMessage(UUID chatId, MessageDTO messageDTO, List<String> recipients) {
        eventPublisher.publishEvent(new ChatEventToPublish(recipients, ChatEvent.messageSent(chatId, messageDTO)));
    }

}
