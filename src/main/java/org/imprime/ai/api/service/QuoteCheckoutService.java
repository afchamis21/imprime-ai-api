package org.imprime.ai.api.service;

import com.stripe.exception.StripeException;
import com.stripe.model.checkout.Session;
import com.stripe.net.RequestOptions;
import com.stripe.param.checkout.SessionCreateParams;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.imprime.ai.api.config.StripeConfigProperties;
import org.imprime.ai.api.http.ServiceContext;
import org.imprime.ai.api.http.response.CheckoutSessionResponse;
import org.imprime.ai.api.model.Order;
import org.imprime.ai.api.model.User;
import org.imprime.ai.api.model.enums.MessageCd;
import org.imprime.ai.api.model.enums.OrderStatusCd;
import org.imprime.ai.api.model.exception.BadGatewayException;
import org.imprime.ai.api.model.exception.ConflictException;
import org.imprime.ai.api.model.exception.ForbiddenException;
import org.imprime.ai.api.repo.db.OrderRepository;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class QuoteCheckoutService {
    private static final String SIMULATED_PREFIX = "cs_quote_sim_";
    private final OrderRepository orders;
    private final OrderStatusService statuses;
    private final StripeConfigProperties stripe;

    @Transactional
    public CheckoutSessionResponse create(String orderGuid) {
        User buyer = ServiceContext.getUserOrThrow();
        Order order = lock(orderGuid);
        requireBuyer(order, buyer);
        if (statuses.findLatest(order).getStatusCd() != OrderStatusCd.CONFIRMED
                || order.getVendorId() == null || order.getOrderTotal() == null || order.getOrderTotal().signum() <= 0
                || order.getPaymentConfirmedAt() != null) {
            throw new ConflictException(MessageCd.INVALID_ORDER_STATUS_TRANSITION);
        }

        String existingId = order.getCheckoutSessionId();
        if (canSimulate()) {
            String sessionId = existingId != null ? existingId : SIMULATED_PREFIX + UUID.randomUUID();
            order.setCheckoutSessionId(sessionId);
            return response(order, null, "SIMULATED");
        }

        RequestOptions options = stripeOptions();
        try {
            if (existingId != null) {
                Session existing = Session.retrieve(existingId, options);
                if ("open".equals(existing.getStatus())) return response(order, existing.getUrl(), "CREATED");
                if ("complete".equals(existing.getStatus()) && "paid".equals(existing.getPaymentStatus())) {
                    registerCompletedSession(order, existing);
                    return response(order, null, "PAID");
                }
                if (!"expired".equals(existing.getStatus())) {
                    throw new ConflictException(MessageCd.CONFLICT);
                }
            }

            String returnUrl = checkoutReturnUrl(order.getGuid());
            SessionCreateParams params = SessionCreateParams.builder()
                    .setMode(SessionCreateParams.Mode.PAYMENT)
                    .addPaymentMethodType(SessionCreateParams.PaymentMethodType.CARD)
                    .setClientReferenceId(order.getGuid())
                    .putMetadata("imprimeQuoteOrder", order.getGuid())
                    .setSuccessUrl(returnUrl + "&payment=success&session_id={CHECKOUT_SESSION_ID}")
                    .setCancelUrl(returnUrl + "&payment=cancel")
                    .addLineItem(SessionCreateParams.LineItem.builder()
                            .setQuantity(1L)
                            .setPriceData(SessionCreateParams.LineItem.PriceData.builder()
                                    .setCurrency(stripe.getCurrency())
                                    .setUnitAmount(order.getOrderTotal().movePointRight(2).longValueExact())
                                    .setProductData(SessionCreateParams.LineItem.PriceData.ProductData.builder()
                                            .setName("Impressão 3D sob demanda").build())
                                    .build())
                            .build())
                    .build();
            Session session = Session.create(params, RequestOptions.builder()
                    .setApiKey(stripe.getSecretKey())
                    .setIdempotencyKey("quote-checkout:" + orderGuid + ":" + Objects.toString(existingId, "new"))
                    .build());
            order.setCheckoutSessionId(session.getId());
            return response(order, session.getUrl(), "CREATED");
        } catch (StripeException ex) {
            throw new BadGatewayException(MessageCd.BAD_GATEWAY);
        }
    }

    @Transactional
    public void confirm(String orderGuid, String sessionId) {
        Order order = lock(orderGuid);
        requireBuyer(order, ServiceContext.getUserOrThrow());
        if (!Objects.equals(order.getCheckoutSessionId(), sessionId)) throw new ForbiddenException();
        if (order.getPaymentConfirmedAt() != null) return;

        if (canSimulate() && sessionId.startsWith(SIMULATED_PREFIX)) {
            order.setPaymentConfirmedAt(Instant.now());
            return;
        }

        try {
            registerCompletedSession(order, Session.retrieve(sessionId, stripeOptions()));
        } catch (StripeException ex) {
            throw new BadGatewayException(MessageCd.BAD_GATEWAY);
        }
    }

    @Transactional
    public void handleCompletedSession(Session session) {
        Map<String, String> metadata = session.getMetadata();
        String orderGuid = metadata == null ? null : metadata.get("imprimeQuoteOrder");
        if (orderGuid == null || !"paid".equals(session.getPaymentStatus())) return;
        Order order = lock(orderGuid);
        registerCompletedSession(order, session);
    }

    private void registerCompletedSession(Order order, Session session) {
        if (!Objects.equals(order.getCheckoutSessionId(), session.getId())
                || !Objects.equals(order.getGuid(), session.getClientReferenceId())
                || !"complete".equals(session.getStatus()) || !"paid".equals(session.getPaymentStatus())
                || !stripe.getCurrency().equalsIgnoreCase(session.getCurrency())
                || order.getOrderTotal() == null
                || !Objects.equals(order.getOrderTotal().movePointRight(2).longValueExact(), session.getAmountTotal())) {
            throw new ConflictException(MessageCd.CONFLICT);
        }
        if (order.getPaymentConfirmedAt() == null) order.setPaymentConfirmedAt(Instant.now());
    }

    private CheckoutSessionResponse response(Order order, String url, String status) {
        return new CheckoutSessionResponse(order.getCheckoutSessionId(), url, status, order.getOrderTotal(), stripe.getCurrency());
    }

    private Order lock(String guid) {
        return orders.findLockedByGuid(guid)
                .orElseThrow(() -> new org.imprime.ai.api.model.exception.NotFoundException(MessageCd.ORDER_NOT_FOUND));
    }

    private void requireBuyer(Order order, User user) {
        if (!Objects.equals(order.getBuyerId(), user.getId())) throw new ForbiddenException();
    }

    private boolean canSimulate() {
        return stripe.isAllowSimulatedOrderCheckout() && !StringUtils.hasText(stripe.getSecretKey());
    }

    private RequestOptions stripeOptions() {
        if (!StringUtils.hasText(stripe.getSecretKey())) throw new BadGatewayException(MessageCd.BAD_GATEWAY);
        return RequestOptions.builder().setApiKey(stripe.getSecretKey()).build();
    }

    private String checkoutReturnUrl(String orderGuid) {
        URI configured = URI.create(stripe.getSuccessUrl().replace("{CHECKOUT_SESSION_ID}", "session"));
        return configured.getScheme() + "://" + configured.getAuthority() + "/chat?order_guid=" + orderGuid;
    }
}
