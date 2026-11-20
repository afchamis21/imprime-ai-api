package org.imprime.ai.api.service;

import org.imprime.ai.api.http.ServiceContext;
import org.imprime.ai.api.model.Chat;
import org.imprime.ai.api.model.ChatMessage;
import org.imprime.ai.api.model.Company;
import org.imprime.ai.api.model.Order;
import org.imprime.ai.api.model.OrderStatus;
import org.imprime.ai.api.model.User;
import org.imprime.ai.api.model.enums.MessageCd;
import org.imprime.ai.api.model.enums.MessageType;
import org.imprime.ai.api.model.enums.OrderStatusCd;
import org.imprime.ai.api.model.enums.StatusCd;
import org.imprime.ai.api.model.exception.BadRequestException;
import org.imprime.ai.api.model.exception.ConflictException;
import org.imprime.ai.api.model.exception.ForbiddenException;
import org.imprime.ai.api.repo.db.ChatMessageRepository;
import org.imprime.ai.api.repo.db.ChatRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ChatServiceOfferDecisionTest {

    @Mock
    private CompanyService companyService;
    @Mock
    private OrderService orderService;
    @Mock
    private OrderStatusService orderStatusService;
    @Mock
    private ChatRepository chatRepository;
    @Mock
    private ChatMessageRepository chatMessageRepository;
    @Mock
    private ApplicationEventPublisher eventPublisher;
    @Mock
    private UserService userService;

    @InjectMocks
    private ChatService chatService;

    private UUID chatId;
    private User buyer;
    private User makerUser;
    private Chat chat;
    private Order order;
    private Company company;

    @BeforeEach
    void setUp() {
        chatId = UUID.randomUUID();

        buyer = new User();
        buyer.setId(1L);
        buyer.setGuid("buyer-guid");
        buyer.setFirstName("Lucas");
        buyer.setLastName("Amorim");

        makerUser = new User();
        makerUser.setId(2L);
        makerUser.setGuid("maker-guid");
        makerUser.setFirstName("Studio");
        makerUser.setLastName("3D");

        chat = new Chat();
        chat.setChatId(chatId);
        chat.setOrderGuid("order-guid-1");
        chat.setClientGuid("buyer-guid");
        chat.setCompanyGuid("company-guid-1");
        chat.setStatus(StatusCd.ACTIVE);

        order = new Order();
        order.setId(100L);
        order.setGuid("order-guid-1");
        order.setBuyerId(1L);

        company = new Company();
        company.setId(50L);
        company.setGuid("company-guid-1");
    }

    @AfterEach
    void tearDown() {
        ServiceContext.clear();
    }

    @Test
    void decideOfferByMessageGuidUsesAuthoritativePriceFromDatabase() {
        ServiceContext.getContext().setUser(buyer);

        OrderStatus quoting = new OrderStatus();
        quoting.setStatusCd(OrderStatusCd.QUOTING);

        OrderStatus confirmed = new OrderStatus();
        confirmed.setOrderId(100L);
        confirmed.setStatusCd(OrderStatusCd.CONFIRMED);

        ChatMessage dbOffer = new ChatMessage();
        dbOffer.setGuid("offer-msg-guid");
        dbOffer.setChatId(chatId);
        dbOffer.setSenderId("maker-guid");
        dbOffer.setType(MessageType.OFFER);
        dbOffer.setContent("150.00");

        when(chatRepository.findById(chatId)).thenReturn(Optional.of(chat));
        when(chatRepository.findAllMemberGuid(chatId)).thenReturn(List.of("buyer-guid", "maker-guid"));
        when(orderService.findByGuid("order-guid-1")).thenReturn(Optional.of(order));
        when(orderStatusService.findLatest(order)).thenReturn(quoting);
        when(chatMessageRepository.findByGuidAndChatId("offer-msg-guid", chatId)).thenReturn(Optional.of(dbOffer));
        when(userService.findUserByGuid("maker-guid")).thenReturn(Optional.of(makerUser));
        when(companyService.findByCompanyGuid("company-guid-1")).thenReturn(Optional.of(company));
        when(orderService.acceptOffer("order-guid-1", new BigDecimal("150.00"), company)).thenReturn(confirmed);
        when(chatRepository.findAllByOrderGuid("order-guid-1")).thenReturn(List.of(chat));

        chatService.decideOfferByMessageGuid(chatId, "offer-msg-guid", true, null);

        verify(orderService).acceptOffer("order-guid-1", new BigDecimal("150.00"), company);
    }

    @Test
    void decideOfferByMessageGuidRejectsMakerSelfApprovalWithForbidden() {
        ServiceContext.getContext().setUser(makerUser);

        when(chatRepository.findById(chatId)).thenReturn(Optional.of(chat));
        when(chatRepository.findAllMemberGuid(chatId)).thenReturn(List.of("buyer-guid", "maker-guid"));

        assertThrows(ForbiddenException.class, () ->
                chatService.decideOfferByMessageGuid(chatId, "offer-msg-guid", true, null)
        );
    }

    @Test
    void decideOfferByMessageGuidRejectsNonOfferMessage() {
        ServiceContext.getContext().setUser(buyer);

        OrderStatus quoting = new OrderStatus();
        quoting.setStatusCd(OrderStatusCd.QUOTING);

        ChatMessage regularMessage = new ChatMessage();
        regularMessage.setGuid("text-msg-guid");
        regularMessage.setChatId(chatId);
        regularMessage.setType(MessageType.MESSAGE);
        regularMessage.setContent("150.00");

        when(chatRepository.findById(chatId)).thenReturn(Optional.of(chat));
        when(chatRepository.findAllMemberGuid(chatId)).thenReturn(List.of("buyer-guid", "maker-guid"));
        when(orderService.findByGuid("order-guid-1")).thenReturn(Optional.of(order));
        when(orderStatusService.findLatest(order)).thenReturn(quoting);
        when(chatMessageRepository.findByGuidAndChatId("text-msg-guid", chatId)).thenReturn(Optional.of(regularMessage));

        BadRequestException ex = assertThrows(BadRequestException.class, () ->
                chatService.decideOfferByMessageGuid(chatId, "text-msg-guid", true, null)
        );
        assertEquals(MessageCd.MESSAGE_NOT_OFFER, ex.getMessageCd());
    }

    @Test
    void decideOfferByMessageGuidThrowsConflictWhenOrderIsNotQuoting() {
        ServiceContext.getContext().setUser(buyer);

        OrderStatus confirmed = new OrderStatus();
        confirmed.setStatusCd(OrderStatusCd.CONFIRMED);

        when(chatRepository.findById(chatId)).thenReturn(Optional.of(chat));
        when(chatRepository.findAllMemberGuid(chatId)).thenReturn(List.of("buyer-guid", "maker-guid"));
        when(orderService.findByGuid("order-guid-1")).thenReturn(Optional.of(order));
        when(orderStatusService.findLatest(order)).thenReturn(confirmed);

        assertThrows(ConflictException.class, () ->
                chatService.decideOfferByMessageGuid(chatId, "offer-msg-guid", true, null)
        );
    }

    @Test
    void decideOfferByMessageGuidAllowsMakerToApproveBuyerCounterProposal() {
        ServiceContext.getContext().setUser(makerUser);

        OrderStatus quoting = new OrderStatus();
        quoting.setStatusCd(OrderStatusCd.QUOTING);

        OrderStatus confirmed = new OrderStatus();
        confirmed.setOrderId(100L);
        confirmed.setStatusCd(OrderStatusCd.CONFIRMED);

        ChatMessage buyerCounterOffer = new ChatMessage();
        buyerCounterOffer.setGuid("counter-offer-guid");
        buyerCounterOffer.setChatId(chatId);
        buyerCounterOffer.setSenderId("buyer-guid");
        buyerCounterOffer.setType(MessageType.OFFER);
        buyerCounterOffer.setContent("135.00");

        when(chatRepository.findById(chatId)).thenReturn(Optional.of(chat));
        when(chatRepository.findAllMemberGuid(chatId)).thenReturn(List.of("buyer-guid", "maker-guid"));
        when(orderService.findByGuid("order-guid-1")).thenReturn(Optional.of(order));
        when(orderStatusService.findLatest(order)).thenReturn(quoting);
        when(chatMessageRepository.findByGuidAndChatId("counter-offer-guid", chatId)).thenReturn(Optional.of(buyerCounterOffer));
        when(userService.findUserByGuid("buyer-guid")).thenReturn(Optional.of(buyer));
        when(companyService.findByCompanyGuid("company-guid-1")).thenReturn(Optional.of(company));
        when(orderService.acceptOffer("order-guid-1", new BigDecimal("135.00"), company)).thenReturn(confirmed);
        when(chatRepository.findAllByOrderGuid("order-guid-1")).thenReturn(List.of(chat));

        chatService.decideOfferByMessageGuid(chatId, "counter-offer-guid", true, "Aceito sua contra-proposta");

        verify(orderService).acceptOffer("order-guid-1", new BigDecimal("135.00"), company);
    }
}
