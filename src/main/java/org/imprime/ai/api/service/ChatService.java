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
import org.imprime.ai.api.model.exception.ForbiddenException;
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
            log.warn("User trying to create chat for Order not in Quoting. Order [{}]. Current Status [{}]", order.getId(), latest.getStatusCd());
            throw new ForbiddenException();
        }

        Optional<Company> optionalCompany = companyService.findByCompanyGuid(request.makerGuid());
        Company company = optionalCompany.orElseThrow(() -> new BadRequestException(MessageCd.COMPANY_NOT_FOUND));

        Chat chat = new Chat();
        chat.setOrderGuid(order.getGuid());
        chat.setClientGuid(caller.getGuid());
        chat.setCompanyGuid(company.getGuid());
        chat.setLastMessageAt(Instant.now());

        chat = chatRepository.save(chat);

        sendMessage(chat, request.message());
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

        message = chatMessageRepository.save(message);
        chatRepository.updateLastMessageSentAt(chat.getChatId());

        if (recipients == null) {
            List<String> members = getChatMembers(chat);
            recipients = members.stream().filter(guid -> !guid.equals(caller.getGuid())).toList();
        }

        deliverMessage(chat.getChatId(), MessageDTO.from(message, caller), recipients);
    }

    @Transactional
    public void handleQuoteDecision(UUID chatId, QuoteDecisionRequest request) {
        log.info("Incoming Quote Decision for Chat [{}]", chatId);
        User caller = ServiceContext.getUserOrThrow();
        Chat chat = findChat(chatId);

        MessageDTO quoteMessage = request.message();
        if (!MessageType.OFFER.equals(quoteMessage.type())) {
            log.warn("Quote Decision request received without Quote Message! [{}]", quoteMessage.type());
            throw new BadRequestException(MessageCd.MESSAGE_NOT_OFFER);
        }

        if (request.accept()) {
            acceptOffer(caller, chat, quoteMessage);
        } else {
            declineOffer(caller, chat, quoteMessage);
        }
    }

    @Transactional
    public void acceptOffer(User caller, Chat chat, MessageDTO quoteMessage) {
        log.info("Accepting offer for Chat [{}]", chat.getChatId());
        BigDecimal offer = new BigDecimal(quoteMessage.content());

        Company company = companyService.findByCompanyGuid(chat.getCompanyGuid()).orElseThrow();

        OrderStatus orderStatus = orderService.acceptOffer(chat.getOrderGuid(), offer, company);
        log.info("Order [{}] Confirmed! New Status [{}]", orderStatus.getOrderId(), orderStatus.getStatusCd());

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
        log.info("Rejecting offer for Chat [{}]", chat.getChatId());

        SendMessageRequest messageRequest = new SendMessageRequest(
                "Oferta de R$ %.2f recusada por %s".formatted(
                        new BigDecimal(quoteMessage.content()),
                        caller.getFullName()
                ),
                MessageType.SYSTEM
        );

        sendMessage(chat, messageRequest);
    }

    private @NonNull Chat findChat(UUID chatId) {
        User caller = ServiceContext.getUserOrThrow();

        Chat chat = chatRepository.findById(chatId).orElseThrow(() -> new BadRequestException(MessageCd.CHAT_NOT_FOUND));

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
