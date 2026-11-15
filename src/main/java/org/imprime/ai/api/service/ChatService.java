package org.imprime.ai.api.service;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.imprime.ai.api.http.ServiceContext;
import org.imprime.ai.api.http.request.chat.SendMessageRequest;
import org.imprime.ai.api.http.request.chat.StartChatRequest;
import org.imprime.ai.api.model.*;
import org.imprime.ai.api.model.dto.ChatDTO;
import org.imprime.ai.api.model.dto.MessageDTO;
import org.imprime.ai.api.model.dto.UserDTO;
import org.imprime.ai.api.model.enums.MessageCd;
import org.imprime.ai.api.model.enums.OrderStatusCd;
import org.imprime.ai.api.model.exception.BadRequestException;
import org.imprime.ai.api.model.exception.ForbiddenException;
import org.imprime.ai.api.repo.db.ChatMemberRepository;
import org.imprime.ai.api.repo.db.ChatMessageRepository;
import org.imprime.ai.api.repo.db.ChatRepository;
import org.imprime.ai.api.websocket.chat.ChatEvent;
import org.imprime.ai.api.websocket.chat.ChatEventToPublish;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

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
    private final ChatMemberRepository chatMemberRepository;

    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public ChatDTO start(StartChatRequest request) {
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
        List<User> users = companyService.findAllMembers(company);
        users.add(caller);

        Chat chat = new Chat();
        chat.setOrderId(order.getId());

        chat = chatRepository.save(chat);

        UUID chatId = chat.getChatId();

        List<ChatMember> members = users.stream().map(u -> ChatMember.builder().chatId(chatId).userId(u.getGuid()).build()).toList();

        chatMemberRepository.saveAll(members);

        ChatMessage chatMessage = new ChatMessage();
        chatMessage.setChatId(chatId);
        chatMessage.setSenderId(caller.getGuid());
        chatMessage.setContent(request.message().content());
        chatMessage.setType(request.message().type());
        chatMessage = chatMessageRepository.save(chatMessage);

        MessageDTO messageDTO = MessageDTO.from(chatMessage);
        deliverMessage(chatId, messageDTO, users.stream().filter(u -> !u.getId().equals(caller.getId())).map(User::getGuid).toList());

        return new ChatDTO(chat.getGuid(), users.stream().map(UserDTO::from).toList(), List.of(messageDTO));
    }

    @Transactional
    public void sendMessage(UUID chatId, SendMessageRequest request) {
        User caller = ServiceContext.getUserOrThrow();

        Chat chat = chatRepository.findById(chatId).orElseThrow(() -> new BadRequestException(MessageCd.CHAT_NOT_FOUND));

        boolean isMember = chatMemberRepository.existsByChatIdAndUserId(chat.getChatId(), caller.getGuid());

        if (!isMember) {
            throw new ForbiddenException();
        }

        ChatMessage message = new ChatMessage();
        message.setChatId(chat.getChatId());
        message.setSenderId(caller.getGuid());
        message.setContent(request.content());
        message.setType(request.type());

        message = chatMessageRepository.save(message);

        List<String> recipients = chatMemberRepository
                .findUserIdsByChatId(chat.getChatId())
                .stream().filter(userId -> !userId.equals(caller.getGuid()))
                .toList();
        
        deliverMessage(chat.getChatId(), MessageDTO.from(message), recipients);
    }

    public void deliverMessage(UUID chatId, MessageDTO messageDTO, List<String> recipients) {
        eventPublisher.publishEvent(new ChatEventToPublish(recipients, ChatEvent.messageSent(chatId, messageDTO)));
    }
}
