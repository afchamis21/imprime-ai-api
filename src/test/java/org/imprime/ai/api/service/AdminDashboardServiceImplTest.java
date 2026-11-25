package org.imprime.ai.api.service;

import org.imprime.ai.api.http.response.PageResponse;
import org.imprime.ai.api.http.response.admin.AdminDashboardSummaryResponse;
import org.imprime.ai.api.http.response.admin.AdminSlaAlertResponse;
import org.imprime.ai.api.model.Company;
import org.imprime.ai.api.model.MakerFinanceTransaction;
import org.imprime.ai.api.model.Model3D;
import org.imprime.ai.api.model.Order;
import org.imprime.ai.api.model.OrderItem;
import org.imprime.ai.api.model.OrderStatus;
import org.imprime.ai.api.model.SupportTicket;
import org.imprime.ai.api.model.User;
import org.imprime.ai.api.model.enums.FinanceTransactionStatus;
import org.imprime.ai.api.model.enums.FinanceTransactionType;
import org.imprime.ai.api.model.enums.MakerApprovalStatus;
import org.imprime.ai.api.model.enums.ModelModerationStatus;
import org.imprime.ai.api.model.enums.OrderStatusCd;
import org.imprime.ai.api.model.enums.RefundStatus;
import org.imprime.ai.api.model.enums.ReportStatus;
import org.imprime.ai.api.model.enums.StatusCd;
import org.imprime.ai.api.model.enums.SupportTicketStatus;
import org.imprime.ai.api.repo.db.CompanyRepository;
import org.imprime.ai.api.repo.db.ContentReportRepository;
import org.imprime.ai.api.repo.db.MakerFinanceTransactionRepository;
import org.imprime.ai.api.repo.db.Model3DRepository;
import org.imprime.ai.api.repo.db.OrderItemRepository;
import org.imprime.ai.api.repo.db.OrderRepository;
import org.imprime.ai.api.repo.db.OrderStatusRepository;
import org.imprime.ai.api.repo.db.RefundRequestRepository;
import org.imprime.ai.api.repo.db.SupportTicketRepository;
import org.imprime.ai.api.repo.db.UserRepository;
import org.imprime.ai.api.service.impl.AdminDashboardServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminDashboardServiceImplTest {

    @Mock
    private OrderRepository orderRepository;
    @Mock
    private OrderStatusRepository orderStatusRepository;
    @Mock
    private OrderItemRepository orderItemRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private CompanyRepository companyRepository;
    @Mock
    private Model3DRepository model3DRepository;
    @Mock
    private SupportTicketRepository supportTicketRepository;
    @Mock
    private ContentReportRepository contentReportRepository;
    @Mock
    private RefundRequestRepository refundRequestRepository;
    @Mock
    private MakerFinanceTransactionRepository financeTransactionRepository;
    @Mock
    private OrderViewAssembler orderViewAssembler;

    private AdminDashboardServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new AdminDashboardServiceImpl(
                orderRepository,
                orderStatusRepository,
                orderItemRepository,
                userRepository,
                companyRepository,
                model3DRepository,
                supportTicketRepository,
                contentReportRepository,
                refundRequestRepository,
                financeTransactionRepository,
                new AdminOrderSlaHelper(),
                orderViewAssembler
        );
    }

    @Test
    void getSummaryComputesGmvFeesInProgressTicketsModelsMakersRefundsAndSla() {
        Instant now = Instant.now();

        Order confirmedDelayed = new Order();
        confirmedDelayed.setId(1L);
        confirmedDelayed.setGuid("ord-1");
        confirmedDelayed.setVendorId(10L);
        confirmedDelayed.setOrderTotal(new BigDecimal("200.00"));
        confirmedDelayed.setCreateDt(now.minus(Duration.ofHours(30)));

        Order completedOrder = new Order();
        completedOrder.setId(2L);
        completedOrder.setGuid("ord-2");
        completedOrder.setVendorId(10L);
        completedOrder.setOrderTotal(new BigDecimal("100.00"));
        completedOrder.setCreateDt(now.minus(Duration.ofHours(2)));

        OrderStatus st1 = new OrderStatus();
        st1.setOrderId(1L);
        st1.setStatusCd(OrderStatusCd.CONFIRMED);
        st1.setStatusDate(now.minus(Duration.ofHours(30)));

        OrderStatus st2 = new OrderStatus();
        st2.setOrderId(2L);
        st2.setStatusCd(OrderStatusCd.COMPLETED);
        st2.setStatusDate(now.minus(Duration.ofHours(1)));

        Company vendor = new Company();
        vendor.setId(10L);
        vendor.setName("Maker Alpha");
        vendor.setApprovalStatus(MakerApprovalStatus.APPROVED);

        Company pendingVendor = new Company();
        pendingVendor.setId(11L);
        pendingVendor.setName("Maker Pending");
        pendingVendor.setApprovalStatus(MakerApprovalStatus.PENDING_APPROVAL);

        MakerFinanceTransaction feeTx = new MakerFinanceTransaction();
        feeTx.setOrderId(2L);
        feeTx.setTransactionType(FinanceTransactionType.PLATFORM_FEE);
        feeTx.setTransactionStatus(FinanceTransactionStatus.COMPLETED);
        feeTx.setAmount(new BigDecimal("-15.00"));

        SupportTicket ticketOpen = new SupportTicket();
        ticketOpen.setTicketStatus(SupportTicketStatus.OPEN);
        ticketOpen.setIsDispute(Boolean.FALSE);

        SupportTicket disputeReview = new SupportTicket();
        disputeReview.setTicketStatus(SupportTicketStatus.IN_REVIEW);
        disputeReview.setIsDispute(Boolean.TRUE);

        Model3D pendingModel = new Model3D();
        pendingModel.setModerationStatus(ModelModerationStatus.PENDING_REVIEW);

        Model3D approvedModel = new Model3D();
        approvedModel.setModerationStatus(ModelModerationStatus.APPROVED);

        when(orderRepository.findAllByStatusOrderByCreateDtDesc(StatusCd.ACTIVE))
                .thenReturn(List.of(confirmedDelayed, completedOrder));
        when(orderStatusRepository.findLatestStatusByOrderId(1L)).thenReturn(Optional.of(st1));
        when(orderStatusRepository.findLatestStatusByOrderId(2L)).thenReturn(Optional.of(st2));
        when(financeTransactionRepository.findAllByTransactionTypeAndStatusOrderByTransactionDateDescCreateDtDesc(
                FinanceTransactionType.PLATFORM_FEE, StatusCd.ACTIVE
        )).thenReturn(List.of(feeTx));
        when(companyRepository.findAllByIdIn(any())).thenReturn(List.of(vendor));
        when(companyRepository.findAllByStatus(StatusCd.ACTIVE)).thenReturn(List.of(vendor, pendingVendor));
        when(supportTicketRepository.findAllByStatusOrderByUpdateDtDescCreateDtDesc(StatusCd.ACTIVE))
                .thenReturn(List.of(ticketOpen, disputeReview));
        when(model3DRepository.findAllByStatus(StatusCd.ACTIVE)).thenReturn(List.of(pendingModel, approvedModel));
        when(refundRequestRepository.countByRefundStatusAndStatus(RefundStatus.PENDING, StatusCd.ACTIVE)).thenReturn(3L);
        when(contentReportRepository.countByReportStatusInAndStatus(
                List.of(ReportStatus.OPEN, ReportStatus.UNDER_REVIEW), StatusCd.ACTIVE
        )).thenReturn(4L);
        when(userRepository.findAllByStatus(StatusCd.ACTIVE)).thenReturn(List.of(new User(), new User(), new User()));

        AdminDashboardSummaryResponse summary = service.getSummary("30d");

        assertEquals(new BigDecimal("300.00"), summary.gmvTotal());
        assertEquals(new BigDecimal("35.00"), summary.platformFeeRevenueTotal());
        assertEquals(1L, summary.ordersInProgress());
        assertEquals(1L, summary.ordersCompletedPeriod());
        assertEquals(2L, summary.openTickets());
        assertEquals(1L, summary.openDisputes());
        assertEquals(4L, summary.openReports());
        assertEquals(1L, summary.pendingModels());
        assertEquals(1L, summary.pendingMakers());
        assertEquals(3L, summary.pendingRefunds());
        assertEquals(1L, summary.delayedSlaCount());
        assertEquals(3L, summary.totalUsers());
        assertEquals(1L, summary.activeMakers());
        assertFalse(summary.series().isEmpty());
    }

    @Test
    void listSlaAlertsFiltersBySeverityAndSortsCriticalFirst() {
        Instant now = Instant.now();

        Order criticalOrder = new Order();
        criticalOrder.setId(1L);
        criticalOrder.setGuid("ord-crit");
        criticalOrder.setOrderCode("PED-CRIT");
        criticalOrder.setBuyerId(100L);
        criticalOrder.setVendorId(200L);
        criticalOrder.setOrderTotal(new BigDecimal("180.00"));
        criticalOrder.setCreateDt(now.minus(Duration.ofHours(80)));

        Order warningOrder = new Order();
        warningOrder.setId(2L);
        warningOrder.setGuid("ord-warn");
        warningOrder.setOrderCode("PED-WARN");
        warningOrder.setBuyerId(100L);
        warningOrder.setVendorId(200L);
        warningOrder.setOrderTotal(new BigDecimal("90.00"));
        warningOrder.setCreateDt(now.minus(Duration.ofHours(30)));

        OrderStatus stCrit = new OrderStatus();
        stCrit.setOrderId(1L);
        stCrit.setStatusCd(OrderStatusCd.CONFIRMED);
        stCrit.setStatusDate(now.minus(Duration.ofHours(80)));

        OrderStatus stWarn = new OrderStatus();
        stWarn.setOrderId(2L);
        stWarn.setStatusCd(OrderStatusCd.CONFIRMED);
        stWarn.setStatusDate(now.minus(Duration.ofHours(30)));

        User buyer = new User();
        buyer.setId(100L);
        buyer.setFirstName("Ana");
        buyer.setLastName("Souza");

        Company vendor = new Company();
        vendor.setId(200L);
        vendor.setName("Studio 3D");
        vendor.setMaxLeadTimeDays(3);

        OrderItem critItem = new OrderItem();
        critItem.setOrderId(1L);
        critItem.setProductId(500L);

        when(orderRepository.findAllByStatusOrderByCreateDtDesc(StatusCd.ACTIVE))
                .thenReturn(List.of(warningOrder, criticalOrder));
        when(orderItemRepository.findAllByOrderIdInAndStatus(any(), eq(StatusCd.ACTIVE))).thenReturn(List.of(critItem));
        when(userRepository.findAllById(any())).thenReturn(List.of(buyer));
        when(companyRepository.findAllByIdIn(any())).thenReturn(List.of(vendor));
        when(orderStatusRepository.findLatestStatusByOrderId(1L)).thenReturn(Optional.of(stCrit));
        when(orderStatusRepository.findLatestStatusByOrderId(2L)).thenReturn(Optional.of(stWarn));

        PageResponse<AdminSlaAlertResponse> allAlerts = service.listSlaAlerts(null, PageRequest.of(0, 10));
        assertEquals(2, allAlerts.content().size());
        assertEquals("CRITICAL", allAlerts.content().get(0).severity());
        assertEquals("PED-CRIT", allAlerts.content().get(0).orderCode());
        assertEquals("WARNING", allAlerts.content().get(1).severity());

        PageResponse<AdminSlaAlertResponse> criticalOnly = service.listSlaAlerts("CRITICAL", PageRequest.of(0, 10));
        assertEquals(1, criticalOnly.content().size());
        assertEquals("PED-CRIT", criticalOnly.content().getFirst().orderCode());
    }
}
