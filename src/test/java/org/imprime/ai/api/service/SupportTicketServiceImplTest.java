package org.imprime.ai.api.service;

import org.imprime.ai.api.http.ServiceContext;
import org.imprime.ai.api.http.request.support.AddSupportMessageRequest;
import org.imprime.ai.api.http.request.support.CreateSupportTicketRequest;
import org.imprime.ai.api.http.response.support.SupportTicketDetailResponse;
import org.imprime.ai.api.http.response.support.SupportTicketMessageResponse;
import org.imprime.ai.api.model.Order;
import org.imprime.ai.api.model.SupportTicket;
import org.imprime.ai.api.model.SupportTicketMessage;
import org.imprime.ai.api.model.User;
import org.imprime.ai.api.model.enums.StatusCd;
import org.imprime.ai.api.model.enums.SupportTicketCategory;
import org.imprime.ai.api.model.enums.SupportTicketStatus;
import org.imprime.ai.api.model.exception.BadRequestException;
import org.imprime.ai.api.model.exception.ForbiddenException;
import org.imprime.ai.api.repo.db.OrderRepository;
import org.imprime.ai.api.repo.db.SupportTicketMessageRepository;
import org.imprime.ai.api.repo.db.SupportTicketRepository;
import org.imprime.ai.api.repo.db.UserRepository;
import org.imprime.ai.api.service.impl.SupportTicketServiceImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SupportTicketServiceImplTest {

    @Mock
    private SupportTicketRepository supportTicketRepository;
    @Mock
    private SupportTicketMessageRepository messageRepository;
    @Mock
    private OrderRepository orderRepository;
    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private SupportTicketServiceImpl service;

    private User caller;

    @BeforeEach
    void setUp() {
        caller = new User();
        caller.setId(8L);
        caller.setGuid("user-guid-8");
        caller.setFirstName("Maria");
        caller.setLastName("Silva");
        ServiceContext.getContext().setUser(caller);
    }

    @AfterEach
    void tearDown() {
        ServiceContext.clear();
    }

    @Test
    void createTicketLinksAuthorizedOrderAndPersistsInitialMessage() {
        Order order = new Order();
        order.setId(42L);
        order.setGuid("order-guid-42");
        order.setBuyerId(8L);

        when(orderRepository.findOrderByGuid("order-guid-42")).thenReturn(Optional.of(order));
        when(supportTicketRepository.existsByProtocolCode(anyString())).thenReturn(false);
        when(supportTicketRepository.save(any(SupportTicket.class))).thenAnswer(inv -> {
            SupportTicket t = inv.getArgument(0);
            t.setId(100L);
            t.setGuid("ticket-guid-100");
            return t;
        });
        when(messageRepository.save(any(SupportTicketMessage.class))).thenAnswer(inv -> {
            SupportTicketMessage m = inv.getArgument(0);
            m.setGuid("msg-guid-1");
            return m;
        });

        SupportTicketDetailResponse detail = service.createTicket(new CreateSupportTicketRequest(
                "Atraso no envio",
                SupportTicketCategory.ORDER,
                "order-guid-42",
                "Meu pedido ainda não foi postado."
        ));

        assertEquals("ticket-guid-100", detail.ticketGuid());
        assertTrue(detail.protocolCode().startsWith("SUP-"));
        assertEquals(SupportTicketStatus.OPEN, detail.status());
        assertEquals("order-guid-42", detail.orderGuid());
        assertEquals(1, detail.messages().size());
        assertFalse(detail.messages().getFirst().fromSupport());
    }

    @Test
    void createTicketRejectsForeignOrderReference() {
        Order foreignOrder = new Order();
        foreignOrder.setId(99L);
        foreignOrder.setGuid("order-guid-99");
        foreignOrder.setBuyerId(999L);
        foreignOrder.setVendorId(888L);

        when(orderRepository.findOrderByGuid("order-guid-99")).thenReturn(Optional.of(foreignOrder));

        assertThrows(ForbiddenException.class, () -> service.createTicket(new CreateSupportTicketRequest(
                "Pedido de outro",
                SupportTicketCategory.ORDER,
                "order-guid-99",
                "Mensagem"
        )));
        verify(supportTicketRepository, never()).save(any());
    }

    @Test
    void addMessageRejectsClosedTicketAndReopensWaitingTicket() {
        SupportTicket closedTicket = new SupportTicket();
        closedTicket.setId(10L);
        closedTicket.setGuid("ticket-closed");
        closedTicket.setUserId(8L);
        closedTicket.setTicketStatus(SupportTicketStatus.CLOSED);
        closedTicket.setStatus(StatusCd.ACTIVE);

        when(supportTicketRepository.findByGuidAndStatus("ticket-closed", StatusCd.ACTIVE))
                .thenReturn(Optional.of(closedTicket));

        assertThrows(BadRequestException.class, () ->
                service.addMessage("ticket-closed", new AddSupportMessageRequest("Tentando responder"))
        );

        SupportTicket waitingTicket = new SupportTicket();
        waitingTicket.setId(11L);
        waitingTicket.setGuid("ticket-waiting");
        waitingTicket.setUserId(8L);
        waitingTicket.setTicketStatus(SupportTicketStatus.WAITING_USER);
        waitingTicket.setStatus(StatusCd.ACTIVE);

        when(supportTicketRepository.findByGuidAndStatus("ticket-waiting", StatusCd.ACTIVE))
                .thenReturn(Optional.of(waitingTicket));
        when(supportTicketRepository.save(any(SupportTicket.class))).thenAnswer(inv -> inv.getArgument(0));
        when(messageRepository.save(any(SupportTicketMessage.class))).thenAnswer(inv -> {
            SupportTicketMessage m = inv.getArgument(0);
            m.setGuid("msg-2");
            return m;
        });

        SupportTicketMessageResponse response = service.addMessage("ticket-waiting", new AddSupportMessageRequest("Segue o anexo"));

        assertEquals(SupportTicketStatus.OPEN, waitingTicket.getTicketStatus());
        assertEquals("msg-2", response.guid());
        assertEquals("Maria Silva", response.senderName());
    }
}
