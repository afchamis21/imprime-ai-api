package org.imprime.ai.api.service;

import com.stripe.exception.StripeException;
import com.stripe.model.checkout.Session;
import com.stripe.net.RequestOptions;
import com.stripe.param.checkout.SessionCreateParams;
import jakarta.annotation.Nullable;
import jakarta.transaction.Transactional;
import org.imprime.ai.api.config.StripeConfigProperties;
import org.imprime.ai.api.http.ServiceContext;
import org.imprime.ai.api.http.request.payment.CreateQuoteCheckoutRequest;
import org.imprime.ai.api.http.response.CheckoutSessionResponse;
import org.imprime.ai.api.model.Address;
import org.imprime.ai.api.model.Order;
import org.imprime.ai.api.model.OrderShippingQuote;
import org.imprime.ai.api.model.User;
import org.imprime.ai.api.model.enums.EntityType;
import org.imprime.ai.api.model.enums.MessageCd;
import org.imprime.ai.api.model.enums.OrderStatusCd;
import org.imprime.ai.api.model.enums.StatusCd;
import org.imprime.ai.api.model.exception.BadGatewayException;
import org.imprime.ai.api.model.exception.BadRequestException;
import org.imprime.ai.api.model.exception.ConflictException;
import org.imprime.ai.api.model.exception.ForbiddenException;
import org.imprime.ai.api.notification.event.OrderPurchasedNotificationEvent;
import org.imprime.ai.api.repo.db.AddressRepository;
import org.imprime.ai.api.repo.db.OrderRepository;
import org.imprime.ai.api.repo.db.OrderShippingQuoteRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Service
public class QuoteCheckoutService {
    private static final String SIMULATED_PREFIX = "cs_quote_sim_";
    private final OrderRepository orders;
    private final OrderStatusService statuses;
    private final StripeConfigProperties stripe;
    private final ApplicationEventPublisher eventPublisher;
    private final OrderShippingQuoteRepository shippingQuoteRepository;
    private final AddressRepository addressRepository;

    public QuoteCheckoutService(OrderRepository orders, OrderStatusService statuses, StripeConfigProperties stripe) {
        this(orders, statuses, stripe, null, null, null);
    }

    public QuoteCheckoutService(
            OrderRepository orders,
            OrderStatusService statuses,
            StripeConfigProperties stripe,
            ApplicationEventPublisher eventPublisher) {
        this(orders, statuses, stripe, eventPublisher, null, null);
    }

    @Autowired
    public QuoteCheckoutService(
            OrderRepository orders,
            OrderStatusService statuses,
            StripeConfigProperties stripe,
            ApplicationEventPublisher eventPublisher,
            @Nullable OrderShippingQuoteRepository shippingQuoteRepository,
            @Nullable AddressRepository addressRepository) {
        this.orders = orders;
        this.statuses = statuses;
        this.stripe = stripe;
        this.eventPublisher = eventPublisher;
        this.shippingQuoteRepository = shippingQuoteRepository;
        this.addressRepository = addressRepository;
    }

    @Transactional
    public CheckoutSessionResponse create(String orderGuid) {
        return create(orderGuid, null);
    }

    @Transactional
    public CheckoutSessionResponse create(String orderGuid, @Nullable CreateQuoteCheckoutRequest request) {
        User buyer = ServiceContext.getUserOrThrow();
        Order order = lock(orderGuid);
        requireBuyer(order, buyer);
        requireInstantQuoteSlicingValidated(order);
        if (statuses.findLatest(order).getStatusCd() != OrderStatusCd.CONFIRMED
                || order.getVendorId() == null || order.getOrderTotal() == null || order.getOrderTotal().signum() <= 0
                || order.getPaymentConfirmedAt() != null) {
            throw new ConflictException(MessageCd.INVALID_ORDER_STATUS_TRANSITION);
        }

        BigDecimal serviceSubtotal = resolveServiceSubtotal(order);
        if (request != null && StringUtils.hasText(request.shippingQuoteGuid())) {
            applySelectedShippingQuote(order, buyer, request.shippingQuoteGuid().trim(), request.addressGuid());
            serviceSubtotal = resolveServiceSubtotal(order);
        } else if (order.getPackageWeight() != null && order.getShippingServiceId() == null) {
            throw new BadRequestException(MessageCd.VALIDATION_ERROR);
        }

        String existingId = order.getCheckoutSessionId();
        if (canSimulate()) {
            String sessionId = existingId != null ? existingId : SIMULATED_PREFIX + UUID.randomUUID();
            order.setCheckoutSessionId(sessionId);
            orders.save(order);
            return response(order, null, "SIMULATED");
        }

        RequestOptions options = stripeOptions();
        try {
            long expectedAmountCents = order.getOrderTotal().movePointRight(2).longValueExact();
            if (existingId != null) {
                Session existing = Session.retrieve(existingId, options);
                if ("open".equals(existing.getStatus())) {
                    if (Objects.equals(expectedAmountCents, existing.getAmountTotal())) {
                        return response(order, existing.getUrl(), "CREATED");
                    }
                } else if ("complete".equals(existing.getStatus()) && "paid".equals(existing.getPaymentStatus())) {
                    registerCompletedSession(order, existing);
                    return response(order, null, "PAID");
                } else if (!"expired".equals(existing.getStatus())) {
                    throw new ConflictException(MessageCd.CONFLICT);
                }
            }

            String returnUrl = checkoutReturnUrl(order.getGuid());
            SessionCreateParams.Builder paramsBuilder = SessionCreateParams.builder()
                    .setMode(SessionCreateParams.Mode.PAYMENT)
                    .addPaymentMethodType(SessionCreateParams.PaymentMethodType.CARD)
                    .setClientReferenceId(order.getGuid())
                    .putMetadata("imprimeQuoteOrder", order.getGuid())
                    .setSuccessUrl(returnUrl + "&payment=success&session_id={CHECKOUT_SESSION_ID}")
                    .setCancelUrl(returnUrl + "&payment=cancel");

            BigDecimal shippingCost = nonNullZero(order.getShippingCost());
            if (shippingCost.signum() > 0 && serviceSubtotal.signum() > 0) {
                long serviceCents = serviceSubtotal.movePointRight(2).longValueExact();
                long shippingCents = shippingCost.movePointRight(2).longValueExact();
                paramsBuilder.addLineItem(SessionCreateParams.LineItem.builder()
                        .setQuantity(1L)
                        .setPriceData(SessionCreateParams.LineItem.PriceData.builder()
                                .setCurrency(stripe.getCurrency())
                                .setUnitAmount(serviceCents)
                                .setProductData(SessionCreateParams.LineItem.PriceData.ProductData.builder()
                                        .setName("Impressão 3D sob demanda").build())
                                .build())
                        .build());
                paramsBuilder.addLineItem(SessionCreateParams.LineItem.builder()
                        .setQuantity(1L)
                        .setPriceData(SessionCreateParams.LineItem.PriceData.builder()
                                .setCurrency(stripe.getCurrency())
                                .setUnitAmount(shippingCents)
                                .setProductData(SessionCreateParams.LineItem.PriceData.ProductData.builder()
                                        .setName(buildShippingLineItemName(order)).build())
                                .build())
                        .build());
            } else {
                paramsBuilder.addLineItem(SessionCreateParams.LineItem.builder()
                        .setQuantity(1L)
                        .setPriceData(SessionCreateParams.LineItem.PriceData.builder()
                                .setCurrency(stripe.getCurrency())
                                .setUnitAmount(expectedAmountCents)
                                .setProductData(SessionCreateParams.LineItem.PriceData.ProductData.builder()
                                        .setName("Impressão 3D sob demanda").build())
                                .build())
                        .build());
            }

            Session session = Session.create(paramsBuilder.build(), RequestOptions.builder()
                    .setApiKey(stripe.getSecretKey())
                    .setIdempotencyKey("quote-checkout:" + orderGuid + ":" + expectedAmountCents + ":"
                            + Objects.toString(order.getShippingServiceId(), "none") + ":"
                            + Objects.toString(existingId, "new"))
                    .build());
            order.setCheckoutSessionId(session.getId());
            orders.save(order);
            return response(order, session.getUrl(), "CREATED");
        } catch (StripeException ex) {
            throw new BadGatewayException(MessageCd.BAD_GATEWAY);
        }
    }

    private void applySelectedShippingQuote(
            Order order,
            User buyer,
            String shippingQuoteGuid,
            @Nullable String addressGuid
    ) {
        if (shippingQuoteRepository == null) {
            throw new IllegalStateException("OrderShippingQuoteRepository indisponível.");
        }
        OrderShippingQuote quote = shippingQuoteRepository.findByGuidAndStatus(shippingQuoteGuid, StatusCd.ACTIVE)
                .orElseThrow(() -> new BadRequestException(MessageCd.VALIDATION_ERROR));
        if (!Objects.equals(quote.getBuyerId(), buyer.getId())
                || !Objects.equals(quote.getVendorId(), order.getVendorId())
                || quote.getExpiresAt() == null
                || !quote.getExpiresAt().isAfter(Instant.now())
                || (quote.getUsedOrderId() != null && !Objects.equals(quote.getUsedOrderId(), order.getId()))) {
            throw new BadRequestException(MessageCd.VALIDATION_ERROR);
        }
        if (StringUtils.hasText(addressGuid) && addressRepository != null) {
            Address address = addressRepository.findByGuidAndStatus(addressGuid.trim(), StatusCd.ACTIVE)
                    .orElseThrow(() -> new BadRequestException(MessageCd.INVALID_ADDRESS_GUID));
            if (!EntityType.USER.equals(address.getOwnerType()) || !Objects.equals(address.getOwnerId(), buyer.getId())) {
                throw new ForbiddenException();
            }
            if (!Objects.equals(address.getId(), quote.getDeliveryAddressId())) {
                throw new BadRequestException(MessageCd.VALIDATION_ERROR);
            }
        }

        BigDecimal serviceSubtotal = resolveServiceSubtotal(order);
        BigDecimal quotePrice = nonNullZero(quote.getQuotePrice());
        boolean shippingChanged = !Objects.equals(order.getShippingServiceId(), quote.getServiceId())
                || order.getShippingCost() == null
                || order.getShippingCost().compareTo(quotePrice) != 0
                || !Objects.equals(order.getToAddressId(), quote.getDeliveryAddressId())
                || !Objects.equals(order.getShippingQuoteSnapshotJson(), quote.getSnapshotJson());

        order.setToAddressId(quote.getDeliveryAddressId());
        order.setServiceAmount(serviceSubtotal);
        order.setShippingCost(quotePrice);
        order.setShippingServiceId(quote.getServiceId());
        order.setShippingServiceName(quote.getServiceName());
        order.setShippingQuotePrice(quotePrice);
        order.setShippingDeliveryDays(quote.getDeliveryDays());
        order.setShippingQuoteSnapshotJson(quote.getSnapshotJson());
        order.setCarrierName(quote.getCarrierName());
        order.setOrderTotal(serviceSubtotal.add(quotePrice).setScale(2, RoundingMode.HALF_UP));

        if (shippingChanged && order.getCheckoutSessionId() != null) {
            order.setCheckoutSessionId(null);
        }

        quote.setUsedOrderId(order.getId());
        shippingQuoteRepository.save(quote);
        orders.save(order);
    }

    private void requireInstantQuoteSlicingValidated(Order order) {
        if (order.getPricingSource() != org.imprime.ai.api.model.enums.OrderPricingSource.INSTANT_QUOTE) {
            return;
        }
        org.imprime.ai.api.model.enums.SlicingValidationStatus status = order.getSlicingValidationStatus();
        if (status == org.imprime.ai.api.model.enums.SlicingValidationStatus.PENDING
                || status == org.imprime.ai.api.model.enums.SlicingValidationStatus.RUNNING) {
            throw new ConflictException(MessageCd.SLICE_VALIDATION_PENDING);
        }
        if (status == org.imprime.ai.api.model.enums.SlicingValidationStatus.RECALCULATED) {
            throw new ConflictException(MessageCd.SLICE_CONFIRMATION_REQUIRED);
        }
        if (status != org.imprime.ai.api.model.enums.SlicingValidationStatus.VALIDATED
                || order.getServerValidatedAmount() == null
                || order.getServerValidatedAmount().signum() <= 0) {
            throw new ConflictException(MessageCd.SLICE_VALIDATION_FAILED);
        }
    }

    private BigDecimal resolveServiceSubtotal(Order order) {
        if (order.getPricingSource() == org.imprime.ai.api.model.enums.OrderPricingSource.INSTANT_QUOTE
                && order.getServerValidatedAmount() != null) {
            return order.getServerValidatedAmount().setScale(2, RoundingMode.HALF_UP);
        }
        if (order.getServiceAmount() != null) {
            return order.getServiceAmount().setScale(2, RoundingMode.HALF_UP);
        }
        return nonNullZero(order.getOrderTotal())
                .subtract(nonNullZero(order.getShippingCost()))
                .max(BigDecimal.ZERO)
                .setScale(2, RoundingMode.HALF_UP);
    }

    private static BigDecimal nonNullZero(@Nullable BigDecimal value) {
        return value == null ? BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP) : value.setScale(2, RoundingMode.HALF_UP);
    }

    private static String buildShippingLineItemName(Order order) {
        String carrier = StringUtils.hasText(order.getCarrierName()) ? order.getCarrierName().trim() : null;
        String service = StringUtils.hasText(order.getShippingServiceName()) ? order.getShippingServiceName().trim() : null;
        if (carrier != null && service != null) {
            return "Frete — " + carrier + " · " + service;
        }
        if (service != null) {
            return "Frete — " + service;
        }
        return "Frete e Entrega";
    }

    @Transactional
    public void confirm(String orderGuid, String sessionId) {
        Order order = lock(orderGuid);
        requireBuyer(order, ServiceContext.getUserOrThrow());
        if (!Objects.equals(order.getCheckoutSessionId(), sessionId)) throw new ForbiddenException();
        if (order.getPaymentConfirmedAt() != null) return;

        if (canSimulate() && sessionId.startsWith(SIMULATED_PREFIX)) {
            if (order.getPaymentConfirmedAt() == null) {
                order.setPaymentConfirmedAt(Instant.now());
                publishQuotePurchased(order);
            }
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
        if (order.getPaymentConfirmedAt() == null) {
            order.setPaymentConfirmedAt(Instant.now());
            publishQuotePurchased(order);
        }
    }

    private void publishQuotePurchased(Order order) {
        if (eventPublisher != null && order != null) {
            eventPublisher.publishEvent(new OrderPurchasedNotificationEvent(
                    order.getId(),
                    order.getGuid(),
                    false,
                    true
            ));
        }
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
