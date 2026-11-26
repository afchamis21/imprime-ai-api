package org.imprime.ai.api.service;

import com.stripe.model.checkout.Session;
import org.imprime.ai.api.config.StripeConfigProperties;
import org.imprime.ai.api.model.Order;
import org.imprime.ai.api.model.exception.ConflictException;
import org.imprime.ai.api.repo.db.OrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class QuoteCheckoutServiceTest {
    private final OrderRepository orders = mock(OrderRepository.class);
    private final OrderStatusService statuses = mock(OrderStatusService.class);
    private final QuoteCheckoutService checkout = new QuoteCheckoutService(orders, statuses, new StripeConfigProperties());
    private final Order order = new Order();

    @BeforeEach
    void setUp() {
        order.setGuid("quote-guid");
        order.setCheckoutSessionId("cs_test_order");
        order.setOrderTotal(new BigDecimal("49.90"));
        when(orders.findLockedByGuid(order.getGuid())).thenReturn(Optional.of(order));
    }

    @Test
    void completedSessionRecordsPaymentWithoutStartingProductionAndCanBeReplayed() {
        Session session = completedSession(4990L);

        checkout.handleCompletedSession(session);
        Instant paidAt = order.getPaymentConfirmedAt();
        checkout.handleCompletedSession(session);

        assertNotNull(paidAt);
        assertEquals(paidAt, order.getPaymentConfirmedAt());
        verifyNoInteractions(statuses);
    }

    @Test
    void rejectsCheckoutThatDoesNotMatchTheOrderTotal() {
        Session session = completedSession(499L);

        assertThrows(ConflictException.class, () -> checkout.handleCompletedSession(session));

        assertNull(order.getPaymentConfirmedAt());
        verifyNoInteractions(statuses);
    }

    private Session completedSession(long amount) {
        Session session = new Session();
        session.setId(order.getCheckoutSessionId());
        session.setClientReferenceId(order.getGuid());
        session.setMetadata(Map.of("imprimeQuoteOrder", order.getGuid()));
        session.setStatus("complete");
        session.setPaymentStatus("paid");
        session.setCurrency("brl");
        session.setAmountTotal(amount);
        return session;
    }
}
