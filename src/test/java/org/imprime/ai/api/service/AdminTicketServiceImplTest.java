package org.imprime.ai.api.service;

import org.imprime.ai.api.http.ServiceContext;
import org.imprime.ai.api.http.request.admin.AdminTicketReplyRequest;
import org.imprime.ai.api.http.request.admin.AdminUpdateTicketRequest;
import org.imprime.ai.api.http.request.support.CreateSupportTicketRequest;
import org.imprime.ai.api.http.response.admin.AdminTicketDetailResponse;
import org.imprime.ai.api.http.response.support.SupportTicketDetailResponse;
import org.imprime.ai.api.model.Chat;
import org.imprime.ai.api.model.ChatMessage;
import org.imprime.ai.api.model.Company;
import org.imprime.ai.api.model.Order;
import org.imprime.ai.api.model.OrderStatus;
import org.imprime.ai.api.model.SupportTicket;
import org.imprime.ai.api.model.SupportTicketMessage;
import org.imprime.ai.api.model.User;
import org.imprime.ai.api.model.enums.AuditAction;
import org.imprime.ai.api.model.enums.MessageCd;
import org.imprime.ai.api.model.enums.MessageType;
import org.imprime.ai.api.model.enums.OrderStatusCd;
import org.imprime.ai.api.model.enums.StatusCd;
import org.imprime.ai.api.model.enums.SupportTicketCategory;
import org.imprime.ai.api.model.enums.SupportTicketStatus;
import org.imprime.ai.api.model.enums.TicketPriority;
import org.imprime.ai.api.model.enums.UserRole;
import org.imprime.ai.api.model.exception.BadRequestException;
import org.imprime.ai.api.repo.db.ChatMessageRepository;
import org.imprime.ai.api.repo.db.ChatRepository;
import org.imprime.ai.api.repo.db.CompanyRepository;
import org.imprime.ai.api.repo.db.OrderRepository;
import org.imprime.ai.api.repo.db.OrderStatusRepository;
import org.imprime.ai.api.repo.db.SupportTicketMessageRepository;
import org.imprime.ai.api.repo.db.SupportTicketRepository;
import org.imprime.ai.api.repo.db.UserRepository;
import org.imprime.ai.api.service.impl.AdminTicketServiceImpl;
import org.imprime.ai.api.service.impl.SupportTicketServiceImpl;
import org.imprime.ai.api.util.JsonCodec;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminTicketServiceImplTest {

    @Mock
    private SupportTicketRepository supportTicketRepository;
    @Mock
    private SupportTicketMessageRepository messageRepository;
    @Mock
    private OrderRepository orderRepository;
    @Mock
    private OrderStatusRepository orderStatusRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private CompanyRepository companyRepository;
    @Mock
    private ChatRepository chatRepository;
    @Mock
    private ChatMessageRepository chatMessageRepository;
    @Mock
    private OrderViewAssembler orderViewAssembler;
    @Mock
    private AdminAuditService adminAuditService;

    private AdminTicketServiceImpl service;
    private SupportTicketServiceImpl buyerSupportService;
    private User admin;
    private User buyer;

    @BeforeEach
    void setUp() {
        service = new AdminTicketServiceImpl(
                supportTicketRepository,
                messageRepository,
                orderRepository,
                orderStatusRepository,
                userRepository,
                companyRepository,
                chatRepository,
                chatMessageRepository,
                orderViewAssembler,
                adminAuditService,
                new JsonCodec()
        );
        buyerSupportService = new SupportTicketServiceImpl(
                supportTicketRepository,
                messageRepository,
                orderRepository,
                userRepository
        );

        admin = new User();
        admin.setId(1L);
        admin.setGuid("admin-1");
        admin.setFirstName("Suporte");
        admin.setLastName("Admin");
        admin.setEmail("support@imprime.ai");
        admin.setRole(UserRole.ADMIN);

        buyer = new User();
        buyer.setId(20L);
        buyer.setGuid("buyer-20");
        buyer.setFirstName("Clara");
        buyer.setLastName("Nunes");
        buyer.setEmail("clara@test.com");
        buyer.setRole(UserRole.BUYER);

        ServiceContext.getContext().setUser(admin);
        lenient().when(orderViewAssembler.formatOrderCode(any())).thenReturn("PED-20");
    }

    @AfterEach
    void tearDown() {
        ServiceContext.clear();
    }

    @Test
    void getTicketDetailAggregatesEvidenceAndOrderChatTranscript() {
        SupportTicket dispute = new SupportTicket();
        dispute.setId(50L);
        dispute.setGuid("tkt-50");
        dispute.setProtocolCode("SUP-DISPUTE1");
        dispute.setSubject("Camadas soltas na peça");
        dispute.setCategory(SupportTicketCategory.DISPUTE);
        dispute.setTicketStatus(SupportTicketStatus.OPEN);
        dispute.setPriority(TicketPriority.HIGH);
        dispute.setIsDispute(Boolean.TRUE);
        dispute.setUserId(20L);
        dispute.setOrderId(90L);
        dispute.setCreateDt(Instant.now());

        SupportTicketMessage buyerMsg = new SupportTicketMessage();
        buyerMsg.setGuid("msg-1");
        buyerMsg.setSupportTicketId(50L);
        buyerMsg.setSenderUserId(20L);
        buyerMsg.setFromSupport(Boolean.FALSE);
        buyerMsg.setMessage("Segue foto do defeito");
        buyerMsg.setAttachmentsJson("[\"https://cdn.imprime.ai/ev1.jpg\",\"https://cdn.imprime.ai/ev2.jpg\"]");

        Order order = new Order();
        order.setId(90L);
        order.setGuid("ord-90");
        order.setOrderCode("PED-90");

        OrderStatus orderStatus = new OrderStatus();
        orderStatus.setOrderId(90L);
        orderStatus.setStatusCd(OrderStatusCd.IN_TRANSIT);

        UUID chatId = UUID.randomUUID();
        Chat chat = Chat.builder()
                .chatId(chatId)
                .orderGuid("ord-90")
                .clientGuid("buyer-20")
                .companyGuid("comp-90")
                .build();

        Company maker = new Company();
        maker.setGuid("comp-90");
        maker.setName("Maker Studio");

        ChatMessage cm1 = ChatMessage.builder()
                .chatId(chatId)
                .senderId("buyer-20")
                .content("Quando envia?")
                .type(MessageType.MESSAGE)
                .build();
        ChatMessage cm2 = ChatMessage.builder()
                .chatId(chatId)
                .senderId("comp-90")
                .content("Enviado hoje!")
                .type(MessageType.MESSAGE)
                .build();

        when(supportTicketRepository.findByGuidAndStatus("tkt-50", StatusCd.ACTIVE)).thenReturn(Optional.of(dispute));
        when(messageRepository.findAllBySupportTicketIdAndStatusOrderByCreateDtAsc(50L, StatusCd.ACTIVE))
                .thenReturn(List.of(buyerMsg));
        when(userRepository.findAllById(any())).thenReturn(List.of(buyer));
        when(orderRepository.findById(90L)).thenReturn(Optional.of(order));
        when(orderStatusRepository.findLatestStatusByOrderId(90L)).thenReturn(Optional.of(orderStatus));
        when(chatRepository.findAllByOrderGuid("ord-90")).thenReturn(List.of(chat));
        when(userRepository.findUserByGuid("buyer-20")).thenReturn(Optional.of(buyer));
        when(companyRepository.findByGuid("comp-90")).thenReturn(Optional.of(maker));
        when(chatMessageRepository.findAllByChatIdOrderByCreateDtAsc(chatId)).thenReturn(List.of(cm1, cm2));

        AdminTicketDetailResponse detail = service.getTicketDetail("tkt-50");

        assertTrue(detail.isDispute());
        assertEquals(2, detail.evidenceUrls().size());
        assertEquals("https://cdn.imprime.ai/ev1.jpg", detail.evidenceUrls().getFirst());
        assertEquals(1, detail.orderChats().size());
        assertEquals("Maker Studio", detail.orderChats().getFirst().makerName());
        assertEquals(2, detail.orderChats().getFirst().messages().size());
        assertEquals("MAKER", detail.orderChats().getFirst().messages().get(1).senderRole());
    }

    @Test
    void replyAndUpdateTicketValidateAdminAssigneeAndRecordAudit() {
        SupportTicket ticket = new SupportTicket();
        ticket.setId(51L);
        ticket.setGuid("tkt-51");
        ticket.setProtocolCode("SUP-123456");
        ticket.setSubject("Reembolso");
        ticket.setCategory(SupportTicketCategory.PAYMENT);
        ticket.setTicketStatus(SupportTicketStatus.OPEN);
        ticket.setPriority(TicketPriority.MEDIUM);
        ticket.setIsDispute(Boolean.FALSE);
        ticket.setUserId(20L);

        when(supportTicketRepository.findByGuidAndStatus("tkt-51", StatusCd.ACTIVE)).thenReturn(Optional.of(ticket));
        when(supportTicketRepository.save(any(SupportTicket.class))).thenAnswer(inv -> inv.getArgument(0));
        when(messageRepository.save(any(SupportTicketMessage.class))).thenAnswer(inv -> inv.getArgument(0));
        when(messageRepository.findAllBySupportTicketIdAndStatusOrderByCreateDtAsc(51L, StatusCd.ACTIVE))
                .thenReturn(List.of());
        when(userRepository.findAllById(any())).thenReturn(List.of(buyer, admin));

        AdminTicketDetailResponse replied = service.replyTicket(
                "tkt-51",
                new AdminTicketReplyRequest("Estamos analisando seu caso.", List.of("https://cdn/doc.pdf"), null)
        );
        assertEquals(SupportTicketStatus.WAITING_USER, replied.status());
        assertEquals("admin-1", replied.assignedAdminGuid());
        verify(adminAuditService).record(eq(AuditAction.TICKET_REPLIED), eq("SUPPORT_TICKET"), eq("tkt-51"), any(), any(), any());

        User nonAdmin = new User();
        nonAdmin.setId(99L);
        nonAdmin.setGuid("buyer-99");
        nonAdmin.setRole(UserRole.BUYER);
        nonAdmin.setStatus(StatusCd.ACTIVE);
        when(userRepository.findUserByGuid("buyer-99")).thenReturn(Optional.of(nonAdmin));

        BadRequestException ex = assertThrows(
                BadRequestException.class,
                () -> service.updateTicket(
                        "tkt-51",
                        new AdminUpdateTicketRequest(null, null, "buyer-99", null, null)
                )
        );
        assertEquals(MessageCd.ADMIN_ROLE_REQUIRED, ex.getMessageCd());

        AdminTicketDetailResponse resolved = service.updateTicket(
                "tkt-51",
                new AdminUpdateTicketRequest(
                        SupportTicketStatus.RESOLVED,
                        TicketPriority.URGENT,
                        null,
                        "Reembolso aprovado para o comprador",
                        "Caso encerrado"
                )
        );
        assertEquals(SupportTicketStatus.RESOLVED, resolved.status());
        assertEquals(TicketPriority.URGENT, resolved.priority());
        assertNotNull(resolved.resolvedAt());
        verify(adminAuditService).record(eq(AuditAction.TICKET_UPDATED), eq("SUPPORT_TICKET"), eq("tkt-51"), eq("Caso encerrado"), any(), any());
    }

    @Test
    void buyerSupportTicketServicePersistsDisputeFlagAndAttachments() {
        ServiceContext.getContext().setUser(buyer);
        when(supportTicketRepository.existsByProtocolCode(any())).thenReturn(false);
        when(supportTicketRepository.save(any(SupportTicket.class))).thenAnswer(inv -> {
            SupportTicket t = inv.getArgument(0);
            t.setId(600L);
            t.setGuid("tkt-600");
            return t;
        });
        when(messageRepository.save(any(SupportTicketMessage.class))).thenAnswer(inv -> {
            SupportTicketMessage m = inv.getArgument(0);
            m.setGuid("msg-600");
            return m;
        });

        SupportTicketDetailResponse created = buyerSupportService.createTicket(new CreateSupportTicketRequest(
                "Peça veio quebrada",
                SupportTicketCategory.DISPUTE,
                null,
                "Enviei fotos da embalagem e da peça.",
                true,
                List.of(" https://cdn.imprime.ai/foto1.png ", "https://cdn.imprime.ai/foto1.png")
        ));

        assertEquals(1, created.messages().size());
        assertEquals(List.of("https://cdn.imprime.ai/foto1.png"), created.messages().getFirst().attachments());
    }
}
