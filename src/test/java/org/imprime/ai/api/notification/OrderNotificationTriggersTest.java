package org.imprime.ai.api.notification;

import com.stripe.model.checkout.Session;
import org.imprime.ai.api.config.StripeConfigProperties;
import org.imprime.ai.api.model.Order;
import org.imprime.ai.api.model.OrderStatus;
import org.imprime.ai.api.model.enums.OrderStatusCd;
import org.imprime.ai.api.notification.event.OrderPurchasedNotificationEvent;
import org.imprime.ai.api.notification.event.OrderStatusChangedNotificationEvent;
import org.imprime.ai.api.repo.db.OrderRepository;
import org.imprime.ai.api.repo.db.OrderStatusRepository;
import org.imprime.ai.api.service.OrderStatusService;
import org.imprime.ai.api.service.QuoteCheckoutService;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class OrderNotificationTriggersTest {

    @Test
    void orderStatusServicePublishesOnlyForUserFacingMilestones() {
        OrderStatusRepository repo = mock(OrderStatusRepository.class);
        ApplicationEventPublisher publisher = mock(ApplicationEventPublisher.class);
        OrderStatusService service = new OrderStatusService(repo, publisher);

        Order order = new Order();
        order.setId(1L);
        order.setGuid("ord-1");

        OrderStatus current = new OrderStatus();
        current.setStatusCd(OrderStatusCd.IN_PRODUCTION);

        OrderStatus saved = new OrderStatus();
        saved.setId(99L);
        saved.setStatusCd(OrderStatusCd.READY_TO_SHIP);

        when(repo.findLatestStatusByOrderId(1L)).thenReturn(Optional.of(current));
        when(repo.save(any(OrderStatus.class))).thenReturn(saved);

        service.move(order, OrderStatusCd.READY_TO_SHIP);
        verify(publisher, times(1)).publishEvent(any(OrderStatusChangedNotificationEvent.class));

        // CREATED -> QUOTING não deve disparar notificação
        OrderStatus created = new OrderStatus();
        created.setStatusCd(OrderStatusCd.CREATED);
        when(repo.findLatestStatusByOrderId(1L)).thenReturn(Optional.of(created));
        service.move(order, OrderStatusCd.QUOTING);
        verifyNoMoreInteractions(publisher);
    }

    @Test
    void quoteCheckoutPublishesPurchaseNotificationExactlyOnceOnWebhookReplay() {
        OrderRepository orders = mock(OrderRepository.class);
        OrderStatusService statuses = mock(OrderStatusService.class);
        ApplicationEventPublisher publisher = mock(ApplicationEventPublisher.class);
        QuoteCheckoutService checkout = new QuoteCheckoutService(orders, statuses, new StripeConfigProperties(), publisher);

        Order order = new Order();
        order.setId(7L);
        order.setGuid("quote-guid");
        order.setCheckoutSessionId("cs_test_quote");
        order.setOrderTotal(new BigDecimal("85.00"));
        when(orders.findLockedByGuid("quote-guid")).thenReturn(Optional.of(order));

        Session session = new Session();
        session.setId("cs_test_quote");
        session.setClientReferenceId("quote-guid");
        session.setMetadata(Map.of("imprimeQuoteOrder", "quote-guid"));
        session.setStatus("complete");
        session.setPaymentStatus("paid");
        session.setCurrency("brl");
        session.setAmountTotal(8500L);

        checkout.handleCompletedSession(session);
        checkout.handleCompletedSession(session);

        verify(publisher, times(1)).publishEvent(any(OrderPurchasedNotificationEvent.class));
    }
}
