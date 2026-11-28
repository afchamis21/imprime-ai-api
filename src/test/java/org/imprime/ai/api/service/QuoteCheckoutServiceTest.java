package org.imprime.ai.api.service;

import com.stripe.model.checkout.Session;
import org.imprime.ai.api.config.StripeConfigProperties;
import org.imprime.ai.api.http.ServiceContext;
import org.imprime.ai.api.model.Order;
import org.imprime.ai.api.model.OrderStatus;
import org.imprime.ai.api.model.User;
import org.imprime.ai.api.model.enums.MessageCd;
import org.imprime.ai.api.model.enums.OrderPricingSource;
import org.imprime.ai.api.model.enums.OrderStatusCd;
import org.imprime.ai.api.model.enums.SlicingValidationStatus;
import org.imprime.ai.api.model.exception.ConflictException;
import org.imprime.ai.api.repo.db.OrderRepository;
import org.junit.jupiter.api.AfterEach;
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

    @AfterEach
    void tearDown() {
        ServiceContext.clear();
    }

    @Test
    void instantQuoteOrderBlocksStripeCheckoutUntilValidatedAndConfirmed() {
        User buyer = new User();
        buyer.setId(77L);
        ServiceContext.getContext().setUser(buyer);

        order.setBuyerId(77L);
        order.setVendorId(10L);
        order.setCheckoutSessionId(null);
        order.setPricingSource(OrderPricingSource.INSTANT_QUOTE);
        order.setServerValidatedAmount(new BigDecimal("49.90"));
        order.setServiceAmount(new BigDecimal("49.90"));

        OrderStatus confirmedStatus = new OrderStatus();
        confirmedStatus.setStatusCd(OrderStatusCd.CONFIRMED);
        when(statuses.findLatest(order)).thenReturn(confirmedStatus);

        StripeConfigProperties simStripe = new StripeConfigProperties();
        simStripe.setAllowSimulatedOrderCheckout(true);
        simStripe.setSuccessUrl("https://imprimeai.shop/cart?payment=success&session_id={CHECKOUT_SESSION_ID}");
        QuoteCheckoutService simCheckout = new QuoteCheckoutService(orders, statuses, simStripe);

        order.setSlicingValidationStatus(SlicingValidationStatus.PENDING);
        assertEquals(MessageCd.SLICE_VALIDATION_PENDING,
                assertThrows(ConflictException.class, () -> simCheckout.create("quote-guid")).getMessageCd());

        order.setSlicingValidationStatus(SlicingValidationStatus.RUNNING);
        assertEquals(MessageCd.SLICE_VALIDATION_PENDING,
                assertThrows(ConflictException.class, () -> simCheckout.create("quote-guid")).getMessageCd());

        order.setSlicingValidationStatus(SlicingValidationStatus.RECALCULATED);
        assertEquals(MessageCd.SLICE_CONFIRMATION_REQUIRED,
                assertThrows(ConflictException.class, () -> simCheckout.create("quote-guid")).getMessageCd());

        order.setSlicingValidationStatus(SlicingValidationStatus.REJECTED);
        assertEquals(MessageCd.SLICE_VALIDATION_FAILED,
                assertThrows(ConflictException.class, () -> simCheckout.create("quote-guid")).getMessageCd());

        order.setSlicingValidationStatus(SlicingValidationStatus.VALIDATED);
        order.setServerValidatedAmount(null);
        assertEquals(MessageCd.SLICE_VALIDATION_FAILED,
                assertThrows(ConflictException.class, () -> simCheckout.create("quote-guid")).getMessageCd());

        order.setServerValidatedAmount(new BigDecimal("54.90"));
        order.setOrderTotal(new BigDecimal("54.90"));
        when(orders.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));
        var res = simCheckout.create("quote-guid");
        assertEquals("SIMULATED", res.status());
        assertEquals(new BigDecimal("54.90"), res.totalAmount());
        assertNotNull(order.getCheckoutSessionId());
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
