package org.imprime.ai.api.service;

import org.imprime.ai.api.config.StripeConfigProperties;
import org.imprime.ai.api.http.ServiceContext;
import org.imprime.ai.api.http.request.admin.AdminApproveRefundRequest;
import org.imprime.ai.api.http.request.admin.AdminCancelOrderRequest;
import org.imprime.ai.api.http.request.admin.AdminCreateRefundRequest;
import org.imprime.ai.api.http.request.admin.AdminRejectRefundRequest;
import org.imprime.ai.api.http.response.PageResponse;
import org.imprime.ai.api.http.response.admin.AdminOrderDetailResponse;
import org.imprime.ai.api.http.response.admin.AdminOrderSummaryResponse;
import org.imprime.ai.api.http.response.admin.AdminRefundResponse;
import org.imprime.ai.api.model.Company;
import org.imprime.ai.api.model.MakerFinanceTransaction;
import org.imprime.ai.api.model.Order;
import org.imprime.ai.api.model.OrderItem;
import org.imprime.ai.api.model.OrderStatus;
import org.imprime.ai.api.model.RefundRequest;
import org.imprime.ai.api.model.User;
import org.imprime.ai.api.model.enums.AdminOrderType;
import org.imprime.ai.api.model.enums.AuditAction;
import org.imprime.ai.api.model.enums.FinanceTransactionType;
import org.imprime.ai.api.model.enums.OrderStatusCd;
import org.imprime.ai.api.model.enums.RefundStatus;
import org.imprime.ai.api.model.enums.RefundType;
import org.imprime.ai.api.model.enums.StatusCd;
import org.imprime.ai.api.model.enums.UserRole;
import org.imprime.ai.api.repo.db.CompanyRepository;
import org.imprime.ai.api.repo.db.MakerFinanceTransactionRepository;
import org.imprime.ai.api.repo.db.OrderItemRepository;
import org.imprime.ai.api.repo.db.OrderRepository;
import org.imprime.ai.api.repo.db.OrderStatusRepository;
import org.imprime.ai.api.repo.db.RefundRequestRepository;
import org.imprime.ai.api.repo.db.UserRepository;
import org.imprime.ai.api.service.impl.AdminOrderServiceImpl;
import org.imprime.ai.api.service.impl.AdminRefundServiceImpl;
import org.imprime.ai.api.util.JsonCodec;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
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
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminOrderAndRefundServiceImplTest {

    @Mock
    private OrderRepository orderRepository;
    @Mock
    private OrderItemRepository orderItemRepository;
    @Mock
    private OrderStatusRepository orderStatusRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private CompanyRepository companyRepository;
    @Mock
    private OrderStatusService orderStatusService;
    @Mock
    private OrderViewAssembler orderViewAssembler;
    @Mock
    private RefundRequestRepository refundRequestRepository;
    @Mock
    private MakerFinanceTransactionRepository makerFinanceTransactionRepository;
    @Mock
    private AdminAuditService adminAuditService;

    private AdminRefundServiceImpl refundService;
    private AdminOrderServiceImpl orderService;
    private User admin;

    @BeforeEach
    void setUp() {
        StripeConfigProperties stripeConfig = new StripeConfigProperties();
        AdminOrderSlaHelper slaHelper = new AdminOrderSlaHelper();

        refundService = new AdminRefundServiceImpl(
                refundRequestRepository,
                orderRepository,
                orderStatusRepository,
                orderStatusService,
                userRepository,
                makerFinanceTransactionRepository,
                stripeConfig,
                orderViewAssembler,
                adminAuditService,
                new JsonCodec()
        );

        orderService = new AdminOrderServiceImpl(
                orderRepository,
                orderItemRepository,
                orderStatusRepository,
                userRepository,
                companyRepository,
                orderStatusService,
                orderViewAssembler,
                slaHelper,
                refundService,
                adminAuditService
        );

        admin = new User();
        admin.setId(1L);
        admin.setGuid("admin-1");
        admin.setFirstName("Finance");
        admin.setLastName("Admin");
        admin.setRole(UserRole.ADMIN);
        ServiceContext.getContext().setUser(admin);

        lenient().when(orderViewAssembler.formatOrderCode(any())).thenReturn("PED-CODE");
    }

    @AfterEach
    void tearDown() {
        ServiceContext.clear();
    }

    @Test
    void listOrdersFiltersByOrderTypeAndSlaDelayedOnly() {
        Instant now = Instant.now();

        Order physicalDelayed = new Order();
        physicalDelayed.setId(1L);
        physicalDelayed.setGuid("ord-phys-1");
        physicalDelayed.setOrderCode("PED-1001");
        physicalDelayed.setBuyerId(10L);
        physicalDelayed.setVendorId(20L);
        physicalDelayed.setOrderTotal(new BigDecimal("150.00"));
        physicalDelayed.setShippingCost(new BigDecimal("20.00"));
        physicalDelayed.setCreateDt(now.minus(Duration.ofHours(30)));

        Order digitalOrder = new Order();
        digitalOrder.setId(2L);
        digitalOrder.setGuid("ord-dig-2");
        digitalOrder.setOrderCode("PED-1002");
        digitalOrder.setBuyerId(10L);
        digitalOrder.setOrderTotal(new BigDecimal("40.00"));
        digitalOrder.setCreateDt(now.minus(Duration.ofHours(2)));

        OrderItem physItem = new OrderItem();
        physItem.setOrderId(1L);
        physItem.setProductId(500L);

        OrderItem digItem = new OrderItem();
        digItem.setOrderId(2L);
        digItem.setModel3dId(900L);

        OrderStatus stPhys = new OrderStatus();
        stPhys.setOrderId(1L);
        stPhys.setStatusCd(OrderStatusCd.CONFIRMED);
        stPhys.setStatusDate(now.minus(Duration.ofHours(30)));

        OrderStatus stDig = new OrderStatus();
        stDig.setOrderId(2L);
        stDig.setStatusCd(OrderStatusCd.COMPLETED);
        stDig.setStatusDate(now.minus(Duration.ofHours(1)));

        User buyer = new User();
        buyer.setId(10L);
        buyer.setGuid("buyer-10");
        buyer.setFirstName("Bruno");
        buyer.setLastName("Mendes");
        buyer.setEmail("bruno@test.com");

        Company maker = new Company();
        maker.setId(20L);
        maker.setGuid("maker-20");
        maker.setName("PrintLab");

        when(orderRepository.findAllByStatusOrderByCreateDtDesc(StatusCd.ACTIVE))
                .thenReturn(List.of(physicalDelayed, digitalOrder));
        when(orderItemRepository.findAllByOrderIdInAndStatus(any(), eq(StatusCd.ACTIVE)))
                .thenReturn(List.of(physItem, digItem));
        when(userRepository.findAllById(any())).thenReturn(List.of(buyer));
        when(companyRepository.findAllByIdIn(any())).thenReturn(List.of(maker));
        when(orderStatusRepository.findLatestStatusByOrderId(1L)).thenReturn(Optional.of(stPhys));
        when(orderStatusRepository.findLatestStatusByOrderId(2L)).thenReturn(Optional.of(stDig));

        PageResponse<AdminOrderSummaryResponse> delayedPhysical = orderService.listOrders(
                null, null, AdminOrderType.PHYSICAL, true, null, null, PageRequest.of(0, 10)
        );
        assertEquals(1, delayedPhysical.content().size());
        assertEquals("PED-1001", delayedPhysical.content().getFirst().orderCode());
        assertTrue(delayedPhysical.content().getFirst().slaDelayed());

        PageResponse<AdminOrderSummaryResponse> digitalPage = orderService.listOrders(
                null, null, AdminOrderType.DIGITAL, false, null, null, PageRequest.of(0, 10)
        );
        assertEquals(1, digitalPage.content().size());
        assertEquals("PED-1002", digitalPage.content().getFirst().orderCode());
    }

    @Test
    void cancelOrderWithAutoRefundCancelsCreatesAndProcessesFullRefund() {
        Order order = new Order();
        order.setId(1L);
        order.setGuid("ord-cancel-1");
        order.setOrderCode("PED-CANCEL");
        order.setBuyerId(10L);
        order.setVendorId(20L);
        order.setOrderTotal(new BigDecimal("120.00"));
        order.setCheckoutSessionId("cs_test_sim_abc");

        OrderStatus current = new OrderStatus();
        current.setOrderId(1L);
        current.setStatusCd(OrderStatusCd.CONFIRMED);

        when(orderRepository.findOrderByGuid("ord-cancel-1")).thenReturn(Optional.of(order));
        when(orderStatusRepository.findLatestStatusByOrderId(1L)).thenReturn(Optional.of(current));
        when(orderStatusService.canMoveTo(OrderStatusCd.CONFIRMED, OrderStatusCd.CANCELLED)).thenReturn(true);
        when(orderStatusService.canMoveTo(OrderStatusCd.CONFIRMED, OrderStatusCd.REFUNDED)).thenReturn(true);
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));
        when(refundRequestRepository.findAllByOrderIdAndStatusOrderByCreateDtDesc(1L, StatusCd.ACTIVE))
                .thenReturn(List.of());
        when(refundRequestRepository.save(any(RefundRequest.class))).thenAnswer(inv -> {
            RefundRequest rr = inv.getArgument(0);
            if (rr.getId() == null) {
                rr.setId(77L);
                rr.setGuid("ref-77");
            }
            return rr;
        });
        when(makerFinanceTransactionRepository.save(any(MakerFinanceTransaction.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(orderViewAssembler.toDetailResponse(order)).thenReturn(null);

        AdminOrderDetailResponse result = orderService.cancelOrder(
                "ord-cancel-1",
                new AdminCancelOrderRequest("Maker indisponível", true)
        );

        assertEquals("Maker indisponível", result.cancellationReason());
        verify(orderStatusService).move(order, OrderStatusCd.CANCELLED);
        verify(orderStatusService).move(order, OrderStatusCd.REFUNDED);
        verify(adminAuditService).record(eq(AuditAction.ORDER_CANCELLED), eq("ORDER"), eq("ord-cancel-1"), any(), any(), any());
        verify(adminAuditService).record(eq(AuditAction.REFUND_CREATED), eq("REFUND"), eq("ref-77"), any(), any(), any());
        verify(adminAuditService).record(eq(AuditAction.REFUND_APPROVED), eq("REFUND"), eq("ref-77"), any(), any(), any());

        ArgumentCaptor<MakerFinanceTransaction> ledgerCaptor = ArgumentCaptor.forClass(MakerFinanceTransaction.class);
        verify(makerFinanceTransactionRepository).save(ledgerCaptor.capture());
        assertEquals(FinanceTransactionType.REFUND, ledgerCaptor.getValue().getTransactionType());
        assertEquals(new BigDecimal("-120.00"), ledgerCaptor.getValue().getAmount());
    }

    @Test
    void approvePartialRefundDoesNotMarkOrderFullyRefundedAndRejectRefundRequiresPending() {
        Order order = new Order();
        order.setId(5L);
        order.setGuid("ord-part-5");
        order.setOrderCode("PED-5");
        order.setBuyerId(10L);
        order.setVendorId(20L);
        order.setOrderTotal(new BigDecimal("200.00"));
        order.setCheckoutSessionId("cs_test_local_55");

        RefundRequest pending = new RefundRequest();
        pending.setId(9L);
        pending.setGuid("ref-9");
        pending.setOrderId(5L);
        pending.setRequestedByUserId(10L);
        pending.setRefundType(RefundType.PARTIAL);
        pending.setRequestedAmount(new BigDecimal("50.00"));
        pending.setRefundStatus(RefundStatus.PENDING);
        pending.setReason("Atraso parcial no envio");

        when(refundRequestRepository.findByGuidAndStatus("ref-9", StatusCd.ACTIVE)).thenReturn(Optional.of(pending));
        when(orderRepository.findById(5L)).thenReturn(Optional.of(order));
        when(refundRequestRepository.findAllByOrderIdAndStatusOrderByCreateDtDesc(5L, StatusCd.ACTIVE))
                .thenReturn(List.of(pending));
        when(refundRequestRepository.save(any(RefundRequest.class))).thenAnswer(inv -> inv.getArgument(0));
        when(makerFinanceTransactionRepository.save(any(MakerFinanceTransaction.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        AdminRefundResponse approved = refundService.approveRefund(
                "ref-9",
                new AdminApproveRefundRequest(new BigDecimal("40.00"), "Aprovado parcialmente")
        );

        assertEquals(RefundStatus.PROCESSED, approved.refundStatus());
        assertEquals(new BigDecimal("40.00"), approved.approvedAmount());
        assertTrue(approved.stripeRefundId().startsWith("re_sim_"));
        verify(orderStatusService, never()).move(any(), eq(OrderStatusCd.REFUNDED));

        RefundRequest pendingForReject = new RefundRequest();
        pendingForReject.setId(10L);
        pendingForReject.setGuid("ref-10");
        pendingForReject.setOrderId(5L);
        pendingForReject.setRequestedByUserId(10L);
        pendingForReject.setRefundType(RefundType.PARTIAL);
        pendingForReject.setRequestedAmount(new BigDecimal("30.00"));
        pendingForReject.setRefundStatus(RefundStatus.PENDING);

        when(refundRequestRepository.findByGuidAndStatus("ref-10", StatusCd.ACTIVE))
                .thenReturn(Optional.of(pendingForReject));

        AdminRefundResponse rejected = refundService.rejectRefund(
                "ref-10",
                new AdminRejectRefundRequest("Peça entregue dentro das tolerâncias")
        );
        assertEquals(RefundStatus.REJECTED, rejected.refundStatus());
        assertEquals("Peça entregue dentro das tolerâncias", rejected.rejectionReason());
        verify(adminAuditService).record(eq(AuditAction.REFUND_REJECTED), eq("REFUND"), eq("ref-10"), any(), any(), any());
    }
}
