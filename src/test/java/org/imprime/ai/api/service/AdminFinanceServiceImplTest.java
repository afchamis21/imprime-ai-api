package org.imprime.ai.api.service;

import org.imprime.ai.api.http.ServiceContext;
import org.imprime.ai.api.http.request.admin.AdminPayoutCompleteRequest;
import org.imprime.ai.api.http.request.admin.AdminPayoutFailRequest;
import org.imprime.ai.api.http.response.PageResponse;
import org.imprime.ai.api.http.response.admin.AdminEscrowItemResponse;
import org.imprime.ai.api.http.response.admin.AdminFinanceSummaryResponse;
import org.imprime.ai.api.http.response.admin.AdminPayoutItemResponse;
import org.imprime.ai.api.model.Company;
import org.imprime.ai.api.model.DesignerFinanceTransaction;
import org.imprime.ai.api.model.MakerFinanceTransaction;
import org.imprime.ai.api.model.Order;
import org.imprime.ai.api.model.OrderStatus;
import org.imprime.ai.api.model.RefundRequest;
import org.imprime.ai.api.model.SupportTicket;
import org.imprime.ai.api.model.User;
import org.imprime.ai.api.model.enums.AdminPayoutBeneficiaryType;
import org.imprime.ai.api.model.enums.AuditAction;
import org.imprime.ai.api.model.enums.FinanceTransactionStatus;
import org.imprime.ai.api.model.enums.FinanceTransactionType;
import org.imprime.ai.api.model.enums.MessageCd;
import org.imprime.ai.api.model.enums.OrderStatusCd;
import org.imprime.ai.api.model.enums.PixKeyType;
import org.imprime.ai.api.model.enums.RefundStatus;
import org.imprime.ai.api.model.enums.StatusCd;
import org.imprime.ai.api.model.enums.SupportTicketStatus;
import org.imprime.ai.api.model.enums.UserRole;
import org.imprime.ai.api.model.exception.ConflictException;
import org.imprime.ai.api.repo.db.CompanyRepository;
import org.imprime.ai.api.repo.db.DesignerFinanceTransactionRepository;
import org.imprime.ai.api.repo.db.MakerFinanceTransactionRepository;
import org.imprime.ai.api.repo.db.OrderRepository;
import org.imprime.ai.api.repo.db.OrderStatusRepository;
import org.imprime.ai.api.repo.db.RefundRequestRepository;
import org.imprime.ai.api.repo.db.SupportTicketRepository;
import org.imprime.ai.api.repo.db.UserRepository;
import org.imprime.ai.api.service.impl.AdminFinanceServiceImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminFinanceServiceImplTest {

    @Mock
    private MakerFinanceTransactionRepository makerFinanceTransactionRepository;
    @Mock
    private DesignerFinanceTransactionRepository designerFinanceTransactionRepository;
    @Mock
    private OrderRepository orderRepository;
    @Mock
    private OrderStatusRepository orderStatusRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private CompanyRepository companyRepository;
    @Mock
    private RefundRequestRepository refundRequestRepository;
    @Mock
    private SupportTicketRepository supportTicketRepository;
    @Mock
    private OrderViewAssembler orderViewAssembler;
    @Mock
    private AdminAuditService adminAuditService;

    private AdminFinanceServiceImpl service;
    private User admin;

    @BeforeEach
    void setUp() {
        service = new AdminFinanceServiceImpl(
                makerFinanceTransactionRepository,
                designerFinanceTransactionRepository,
                orderRepository,
                orderStatusRepository,
                userRepository,
                companyRepository,
                refundRequestRepository,
                supportTicketRepository,
                orderViewAssembler,
                adminAuditService
        );
        admin = new User();
        admin.setId(1L);
        admin.setGuid("admin-fin-1");
        admin.setFirstName("Treasurer");
        admin.setLastName("Admin");
        admin.setRole(UserRole.ADMIN);
        ServiceContext.getContext().setUser(admin);
        lenient().when(orderViewAssembler.formatOrderCode(any())).thenReturn("PED-FIN");
    }

    @AfterEach
    void tearDown() {
        ServiceContext.clear();
    }

    @Test
    void getSummaryAndListEscrowConsolidateFeesEscrowAndPayouts() {
        Order inProduction = new Order();
        inProduction.setId(1L);
        inProduction.setGuid("ord-1");
        inProduction.setOrderCode("PED-1");
        inProduction.setBuyerId(10L);
        inProduction.setVendorId(20L);
        inProduction.setOrderTotal(new BigDecimal("100.00"));

        Order completedDisputed = new Order();
        completedDisputed.setId(2L);
        completedDisputed.setGuid("ord-2");
        completedDisputed.setOrderCode("PED-2");
        completedDisputed.setBuyerId(10L);
        completedDisputed.setVendorId(20L);
        completedDisputed.setOrderTotal(new BigDecimal("200.00"));

        OrderStatus st1 = new OrderStatus();
        st1.setOrderId(1L);
        st1.setStatusCd(OrderStatusCd.IN_PRODUCTION);

        OrderStatus st2 = new OrderStatus();
        st2.setOrderId(2L);
        st2.setStatusCd(OrderStatusCd.COMPLETED);

        SupportTicket openDispute = new SupportTicket();
        openDispute.setIsDispute(Boolean.TRUE);
        openDispute.setOrderId(2L);
        openDispute.setTicketStatus(SupportTicketStatus.OPEN);

        RefundRequest settledPartial = new RefundRequest();
        settledPartial.setOrderId(2L);
        settledPartial.setRefundStatus(RefundStatus.PROCESSED);
        settledPartial.setApprovedAmount(new BigDecimal("50.00"));

        MakerFinanceTransaction makerPaid = new MakerFinanceTransaction();
        makerPaid.setGuid("m-pay-1");
        makerPaid.setTransactionType(FinanceTransactionType.PAYOUT);
        makerPaid.setTransactionStatus(FinanceTransactionStatus.COMPLETED);
        makerPaid.setAmount(new BigDecimal("-120.00"));

        DesignerFinanceTransaction designerPending = new DesignerFinanceTransaction();
        designerPending.setGuid("d-pay-1");
        designerPending.setTransactionType(FinanceTransactionType.PAYOUT);
        designerPending.setTransactionStatus(FinanceTransactionStatus.PENDING);
        designerPending.setAmount(new BigDecimal("80.00"));

        User buyer = new User();
        buyer.setId(10L);
        buyer.setGuid("buyer-10");
        buyer.setFirstName("Marcos");
        buyer.setLastName("Alves");

        Company maker = new Company();
        maker.setId(20L);
        maker.setGuid("maker-20");
        maker.setName("Studio Forge");

        when(orderRepository.findAllByStatusOrderByCreateDtDesc(StatusCd.ACTIVE))
                .thenReturn(List.of(inProduction, completedDisputed));
        when(orderStatusRepository.findLatestStatusByOrderId(1L)).thenReturn(Optional.of(st1));
        when(orderStatusRepository.findLatestStatusByOrderId(2L)).thenReturn(Optional.of(st2));
        when(makerFinanceTransactionRepository.findAllByTransactionTypeAndStatusOrderByTransactionDateDescCreateDtDesc(
                FinanceTransactionType.PLATFORM_FEE, StatusCd.ACTIVE
        )).thenReturn(List.of());
        when(makerFinanceTransactionRepository.findAllByTransactionTypeAndStatusOrderByTransactionDateDescCreateDtDesc(
                FinanceTransactionType.PAYOUT, StatusCd.ACTIVE
        )).thenReturn(List.of(makerPaid));
        when(designerFinanceTransactionRepository.findAllByTransactionTypeAndStatusOrderByTransactionDateDescCreateDtDesc(
                FinanceTransactionType.PAYOUT, StatusCd.ACTIVE
        )).thenReturn(List.of(designerPending));
        when(supportTicketRepository.findAllByStatusOrderByUpdateDtDescCreateDtDesc(StatusCd.ACTIVE))
                .thenReturn(List.of(openDispute));
        when(refundRequestRepository.findAllByStatusOrderByCreateDtDesc(StatusCd.ACTIVE))
                .thenReturn(List.of(settledPartial));
        when(userRepository.findAllById(any())).thenReturn(List.of(buyer));
        when(companyRepository.findAllByIdIn(any())).thenReturn(List.of(maker));

        AdminFinanceSummaryResponse summary = service.getSummary();

        assertEquals(new BigDecimal("300.00"), summary.gmvTotal());
        assertEquals(new BigDecimal("30.00"), summary.platformFeeRevenue());
        // Order 1 held: 100 - 10 = 90; Order 2 held (dispute): 200 - 20 - 50 = 130; total = 220
        assertEquals(new BigDecimal("220.00"), summary.escrowBalance());
        assertEquals(2L, summary.escrowOrdersCount());
        assertEquals(new BigDecimal("120.00"), summary.makerPayoutsPaid());
        assertEquals(new BigDecimal("80.00"), summary.designerPayoutsPending());
        assertEquals(1L, summary.pendingPayoutsCount());
        assertEquals(new BigDecimal("50.00"), summary.refundsTotal());
        assertEquals(new BigDecimal("25.00"), summary.netPlatformRevenue());

        PageResponse<AdminEscrowItemResponse> disputedOnly = service.listEscrow(null, null, true, PageRequest.of(0, 10));
        assertEquals(1, disputedOnly.content().size());
        assertEquals("DISPUTE_OPEN", disputedOnly.content().getFirst().holdReason());
    }

    @Test
    void listAndProcessPayoutsCompleteAndFailWithAudit() {
        Company maker = new Company();
        maker.setId(20L);
        maker.setGuid("maker-20");
        maker.setOwnerId(21L);
        maker.setName("Print House");

        User makerOwner = new User();
        makerOwner.setId(21L);
        makerOwner.setGuid("owner-21");
        makerOwner.setFirstName("Owner");
        makerOwner.setLastName("Maker");
        makerOwner.setEmail("owner@printhouse.ai");

        User designer = new User();
        designer.setId(30L);
        designer.setGuid("des-30");
        designer.setFirstName("Julia");
        designer.setLastName("Mesh");
        designer.setEmail("julia@mesh.ai");

        MakerFinanceTransaction mTx = new MakerFinanceTransaction();
        mTx.setGuid("payout-m-1");
        mTx.setCompanyId(20L);
        mTx.setTransactionType(FinanceTransactionType.PAYOUT);
        mTx.setTransactionStatus(FinanceTransactionStatus.PROCESSING);
        mTx.setAmount(new BigDecimal("-250.00"));
        mTx.setTransactionDate(LocalDate.of(2026, 11, 20));
        mTx.setTitle("Repasse PIX Maker");
        mTx.setPixKeyType(PixKeyType.CNPJ);
        mTx.setPixKeyMasked("12.***.***/0001-99");

        DesignerFinanceTransaction dTx = new DesignerFinanceTransaction();
        dTx.setGuid("payout-d-1");
        dTx.setDesignerUserId(30L);
        dTx.setTransactionType(FinanceTransactionType.PAYOUT);
        dTx.setTransactionStatus(FinanceTransactionStatus.COMPLETED);
        dTx.setAmount(new BigDecimal("75.00"));
        dTx.setTransactionDate(LocalDate.of(2026, 11, 19));
        dTx.setTitle("Royalties 3D");

        when(makerFinanceTransactionRepository.findAllByTransactionTypeAndStatusOrderByTransactionDateDescCreateDtDesc(
                FinanceTransactionType.PAYOUT, StatusCd.ACTIVE
        )).thenReturn(List.of(mTx));
        when(designerFinanceTransactionRepository.findAllByTransactionTypeAndStatusOrderByTransactionDateDescCreateDtDesc(
                FinanceTransactionType.PAYOUT, StatusCd.ACTIVE
        )).thenReturn(List.of(dTx));
        when(companyRepository.findAllByIdIn(any())).thenReturn(List.of(maker));
        when(userRepository.findAllById(any())).thenReturn(List.of(makerOwner, designer, admin));

        PageResponse<AdminPayoutItemResponse> allPayouts = service.listPayouts(
                null, null, null, null, null, PageRequest.of(0, 10)
        );
        assertEquals(2, allPayouts.content().size());
        assertEquals("payout-m-1", allPayouts.content().getFirst().payoutGuid());

        PageResponse<AdminPayoutItemResponse> makerFilter = service.listPayouts(
                AdminPayoutBeneficiaryType.MAKER, null, null, null, "Print", PageRequest.of(0, 10)
        );
        assertEquals(1, makerFilter.content().size());
        assertEquals(new BigDecimal("250.00"), makerFilter.content().getFirst().amount());

        when(makerFinanceTransactionRepository.findByGuidAndStatus("payout-m-1", StatusCd.ACTIVE))
                .thenReturn(Optional.of(mTx));
        when(makerFinanceTransactionRepository.save(any(MakerFinanceTransaction.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(companyRepository.findById(20L)).thenReturn(Optional.of(maker));

        AdminPayoutItemResponse completed = service.completePayout(
                "payout-m-1",
                new AdminPayoutCompleteRequest("E999PIX001", "Pago via lote")
        );
        assertEquals(FinanceTransactionStatus.COMPLETED, completed.status());
        assertEquals("E999PIX001", completed.reference());
        verify(adminAuditService).record(eq(AuditAction.PAYOUT_COMPLETED), eq("PAYOUT"), eq("payout-m-1"), eq("Pago via lote"), any(), any());

        when(makerFinanceTransactionRepository.findByGuidAndStatus("payout-d-1", StatusCd.ACTIVE))
                .thenReturn(Optional.empty());
        when(designerFinanceTransactionRepository.findByGuidAndStatus("payout-d-1", StatusCd.ACTIVE))
                .thenReturn(Optional.of(dTx));

        ConflictException alreadyDone = assertThrows(
                ConflictException.class,
                () -> service.failPayout("payout-d-1", new AdminPayoutFailRequest("Chave inválida"))
        );
        assertEquals(MessageCd.PAYOUT_INVALID_STATE, alreadyDone.getMessageCd());

        dTx.setTransactionStatus(FinanceTransactionStatus.PENDING);
        when(designerFinanceTransactionRepository.save(any(DesignerFinanceTransaction.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        AdminPayoutItemResponse failed = service.failPayout("payout-d-1", new AdminPayoutFailRequest("Chave PIX inativa"));
        assertEquals(FinanceTransactionStatus.FAILED, failed.status());
        assertEquals("Chave PIX inativa", failed.failureReason());
        verify(adminAuditService).record(eq(AuditAction.PAYOUT_FAILED), eq("PAYOUT"), eq("payout-d-1"), eq("Chave PIX inativa"), any(), any());
    }
}
