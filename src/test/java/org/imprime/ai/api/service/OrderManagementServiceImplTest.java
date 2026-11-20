package org.imprime.ai.api.service;

import org.imprime.ai.api.http.ServiceContext;
import org.imprime.ai.api.http.request.order.UpdateOrderStatusRequest;
import org.imprime.ai.api.http.response.order.OrderDetailResponse;
import org.imprime.ai.api.model.Company;
import org.imprime.ai.api.model.Order;
import org.imprime.ai.api.model.OrderStatus;
import org.imprime.ai.api.model.User;
import org.imprime.ai.api.model.enums.OrderStatusCd;
import org.imprime.ai.api.model.exception.ConflictException;
import org.imprime.ai.api.model.exception.ForbiddenException;
import org.imprime.ai.api.model.exception.NotFoundException;
import org.imprime.ai.api.repo.db.ChatRepository;
import org.imprime.ai.api.repo.db.CompanyRepository;
import org.imprime.ai.api.repo.db.OrderRepository;
import org.imprime.ai.api.service.impl.OrderManagementServiceImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderManagementServiceImplTest {

    @Mock
    private OrderRepository orderRepository;
    @Mock
    private OrderStatusService orderStatusService;
    @Mock
    private ChatRepository chatRepository;
    @Mock
    private CompanyRepository companyRepository;
    @Mock
    private OrderViewAssembler orderViewAssembler;

    @InjectMocks
    private OrderManagementServiceImpl service;

    private User buyer;
    private User makerUser;
    private User outsider;
    private Order order;

    @BeforeEach
    void setUp() {
        buyer = new User();
        buyer.setId(1L);
        buyer.setGuid("buyer-guid");

        makerUser = new User();
        makerUser.setId(2L);
        makerUser.setGuid("maker-user-guid");
        makerUser.setCompanyId(50L);

        outsider = new User();
        outsider.setId(3L);
        outsider.setGuid("outsider-guid");

        order = new Order();
        order.setId(10L);
        order.setGuid("order-guid-10");
        order.setBuyerId(1L);
        order.setVendorId(50L);
        order.setOrderTotal(new BigDecimal("120.00"));
    }

    @AfterEach
    void tearDown() {
        ServiceContext.clear();
    }

    @Test
    void getOrderDetailAllowsMakerParticipatingInQuotingChat() {
        order.setVendorId(null);
        ServiceContext.getContext().setUser(makerUser);

        OrderStatus quotingStatus = new OrderStatus();
        quotingStatus.setStatusCd(OrderStatusCd.QUOTING);

        Company company = new Company();
        company.setId(50L);
        company.setGuid("company-guid-50");

        OrderDetailResponse expected = new OrderDetailResponse(
                "order-guid-10", "PED-ORDERG", OrderStatusCd.QUOTING,
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                null, null, null, null, null, List.of(), List.of(), Instant.now(), Instant.now()
        );

        when(orderRepository.findOrderByGuid("order-guid-10")).thenReturn(Optional.of(order));
        when(orderStatusService.findLatest(order)).thenReturn(quotingStatus);
        when(companyRepository.findById(50L)).thenReturn(Optional.of(company));
        when(chatRepository.existsByOrderGuidAndCompanyGuid("order-guid-10", "company-guid-50")).thenReturn(true);
        when(orderViewAssembler.toDetailResponse(order)).thenReturn(expected);

        OrderDetailResponse response = service.getOrderDetail("order-guid-10");

        assertEquals("order-guid-10", response.orderGuid());
    }

    @Test
    void getOrderDetailRejectsOutsiderWithForbidden() {
        ServiceContext.getContext().setUser(outsider);
        when(orderRepository.findOrderByGuid("order-guid-10")).thenReturn(Optional.of(order));

        assertThrows(ForbiddenException.class, () -> service.getOrderDetail("order-guid-10"));
    }

    @Test
    void getOrderDetailThrowsNotFoundWhenMissing() {
        ServiceContext.getContext().setUser(buyer);
        when(orderRepository.findOrderByGuid("missing-order")).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class, () -> service.getOrderDetail("missing-order"));
    }

    @Test
    void updateOrderStatusAllowsBuyerToCancelConfirmedOrder() {
        ServiceContext.getContext().setUser(buyer);
        OrderStatus confirmed = new OrderStatus();
        confirmed.setStatusCd(OrderStatusCd.CONFIRMED);

        when(orderRepository.findOrderByGuid("order-guid-10")).thenReturn(Optional.of(order));
        when(orderStatusService.findLatest(order)).thenReturn(confirmed);
        when(orderStatusService.canMoveTo(OrderStatusCd.CONFIRMED, OrderStatusCd.CANCELLED)).thenReturn(true);

        service.updateOrderStatus("order-guid-10", new UpdateOrderStatusRequest(OrderStatusCd.CANCELLED, "Mudança de planos", null));

        assertEquals("Mudança de planos", order.getCancellationReason());
        verify(orderRepository).save(order);
        verify(orderStatusService).move(order, OrderStatusCd.CANCELLED);
        verify(orderViewAssembler).toDetailResponse(order);
    }

    @Test
    void updateOrderStatusPersistsTrackingCodeWhenMovingToInTransit() {
        ServiceContext.getContext().setUser(makerUser);
        OrderStatus inProduction = new OrderStatus();
        inProduction.setStatusCd(OrderStatusCd.IN_PRODUCTION);

        when(orderRepository.findOrderByGuid("order-guid-10")).thenReturn(Optional.of(order));
        when(orderStatusService.findLatest(order)).thenReturn(inProduction);
        when(orderStatusService.canMoveTo(OrderStatusCd.IN_PRODUCTION, OrderStatusCd.IN_TRANSIT)).thenReturn(true);

        service.updateOrderStatus("order-guid-10", new UpdateOrderStatusRequest(OrderStatusCd.IN_TRANSIT, null, "BR123456789BR"));

        assertEquals("BR123456789BR", order.getTrackingCode());
        verify(orderRepository).save(order);
        verify(orderStatusService).move(order, OrderStatusCd.IN_TRANSIT);
    }

    @Test
    void updateOrderStatusAllowsBuyerMovingConfirmedOrderToInProductionAfterPayment() {
        ServiceContext.getContext().setUser(buyer);
        OrderStatus confirmed = new OrderStatus();
        confirmed.setStatusCd(OrderStatusCd.CONFIRMED);

        when(orderRepository.findOrderByGuid("order-guid-10")).thenReturn(Optional.of(order));
        when(orderStatusService.findLatest(order)).thenReturn(confirmed);
        when(orderStatusService.canMoveTo(OrderStatusCd.CONFIRMED, OrderStatusCd.IN_PRODUCTION)).thenReturn(true);

        service.updateOrderStatus("order-guid-10", new UpdateOrderStatusRequest(OrderStatusCd.IN_PRODUCTION, null, null));

        verify(orderStatusService).move(order, OrderStatusCd.IN_PRODUCTION);
        verify(orderViewAssembler).toDetailResponse(order);
    }

    @Test
    void updateOrderStatusThrowsConflictOnInvalidStateMachineTransition() {
        ServiceContext.getContext().setUser(makerUser);
        OrderStatus created = new OrderStatus();
        created.setStatusCd(OrderStatusCd.CREATED);
        order.setVendorId(50L);

        when(orderRepository.findOrderByGuid("order-guid-10")).thenReturn(Optional.of(order));
        when(orderStatusService.findLatest(order)).thenReturn(created);
        when(orderStatusService.canMoveTo(OrderStatusCd.CREATED, OrderStatusCd.COMPLETED)).thenReturn(false);

        assertThrows(ConflictException.class, () ->
                service.updateOrderStatus("order-guid-10", new UpdateOrderStatusRequest(OrderStatusCd.COMPLETED, null, null))
        );
    }
}
