package org.imprime.ai.api.service.impl;

import com.stripe.Stripe;
import com.stripe.model.Refund;
import com.stripe.model.checkout.Session;
import com.stripe.param.RefundCreateParams;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.imprime.ai.api.config.StripeConfigProperties;
import org.imprime.ai.api.http.ServiceContext;
import org.imprime.ai.api.http.request.admin.AdminApproveRefundRequest;
import org.imprime.ai.api.http.request.admin.AdminCreateRefundRequest;
import org.imprime.ai.api.http.request.admin.AdminRejectRefundRequest;
import org.imprime.ai.api.http.response.PageResponse;
import org.imprime.ai.api.http.response.admin.AdminRefundResponse;
import org.imprime.ai.api.model.MakerFinanceTransaction;
import org.imprime.ai.api.model.Order;
import org.imprime.ai.api.model.OrderStatus;
import org.imprime.ai.api.model.RefundRequest;
import org.imprime.ai.api.model.User;
import org.imprime.ai.api.model.enums.AuditAction;
import org.imprime.ai.api.model.enums.FinanceTransactionStatus;
import org.imprime.ai.api.model.enums.FinanceTransactionType;
import org.imprime.ai.api.model.enums.MessageCd;
import org.imprime.ai.api.model.enums.OrderStatusCd;
import org.imprime.ai.api.model.enums.RefundStatus;
import org.imprime.ai.api.model.enums.RefundType;
import org.imprime.ai.api.model.enums.StatusCd;
import org.imprime.ai.api.model.exception.BadGatewayException;
import org.imprime.ai.api.model.exception.BadRequestException;
import org.imprime.ai.api.model.exception.ConflictException;
import org.imprime.ai.api.model.exception.NotFoundException;
import org.imprime.ai.api.notification.event.RefundDecisionNotificationEvent;
import org.imprime.ai.api.repo.db.MakerFinanceTransactionRepository;
import org.imprime.ai.api.repo.db.OrderRepository;
import org.imprime.ai.api.repo.db.OrderStatusRepository;
import org.imprime.ai.api.repo.db.RefundRequestRepository;
import org.imprime.ai.api.repo.db.UserRepository;
import org.imprime.ai.api.service.AdminAuditService;
import org.imprime.ai.api.service.AdminRefundService;
import org.imprime.ai.api.service.OrderStatusService;
import org.imprime.ai.api.service.OrderViewAssembler;
import org.imprime.ai.api.util.JsonCodec;
import org.imprime.ai.api.util.PageUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AdminRefundServiceImpl implements AdminRefundService {
    private static final Set<RefundStatus> SETTLED_REFUND_STATUSES = Set.of(
            RefundStatus.APPROVED,
            RefundStatus.PROCESSED
    );

    private final RefundRequestRepository refundRequestRepository;
    private final OrderRepository orderRepository;
    private final OrderStatusRepository orderStatusRepository;
    private final OrderStatusService orderStatusService;
    private final UserRepository userRepository;
    private final MakerFinanceTransactionRepository makerFinanceTransactionRepository;
    private final StripeConfigProperties stripeConfig;
    private final OrderViewAssembler orderViewAssembler;
    private final AdminAuditService adminAuditService;
    private final JsonCodec jsonCodec;
    @Autowired(required = false)
    private ApplicationEventPublisher eventPublisher;

    @Override
    public PageResponse<AdminRefundResponse> listRefunds(
            RefundStatus status,
            RefundType refundType,
            String q,
            Pageable pageable
    ) {
        List<RefundRequest> all = refundRequestRepository.findAllByStatusOrderByCreateDtDesc(StatusCd.ACTIVE);
        if (all.isEmpty()) {
            return PageUtils.paginate(List.of(), pageable);
        }

        Set<Long> orderIds = new HashSet<>();
        Set<Long> userIds = new HashSet<>();
        for (RefundRequest rr : all) {
            if (rr.getOrderId() != null) orderIds.add(rr.getOrderId());
            if (rr.getRequestedByUserId() != null) userIds.add(rr.getRequestedByUserId());
            if (rr.getReviewedByUserId() != null) userIds.add(rr.getReviewedByUserId());
        }

        Map<Long, Order> ordersById = orderIds.isEmpty()
                ? Map.of()
                : orderRepository.findAllById(orderIds).stream()
                .collect(Collectors.toMap(Order::getId, o -> o, (a, b) -> a));

        Map<Long, User> usersById = userIds.isEmpty()
                ? Map.of()
                : userRepository.findAllById(userIds).stream()
                .collect(Collectors.toMap(User::getId, u -> u, (a, b) -> a));

        String normalizedQuery = trimToNull(q);

        List<AdminRefundResponse> filtered = all.stream()
                .filter(rr -> status == null || status.equals(rr.getRefundStatus()))
                .filter(rr -> refundType == null || refundType.equals(rr.getRefundType()))
                .map(rr -> toResponse(
                        rr,
                        ordersById.get(rr.getOrderId()),
                        usersById.get(rr.getRequestedByUserId()),
                        rr.getReviewedByUserId() != null ? usersById.get(rr.getReviewedByUserId()) : null
                ))
                .filter(resp -> matchesQuery(resp, normalizedQuery))
                .toList();

        return PageUtils.paginate(filtered, pageable);
    }

    @Override
    public List<AdminRefundResponse> listRefundsForOrder(Long orderId) {
        if (orderId == null) {
            return List.of();
        }
        Order order = orderRepository.findById(orderId).orElse(null);
        List<RefundRequest> list = refundRequestRepository.findAllByOrderIdAndStatusOrderByCreateDtDesc(orderId, StatusCd.ACTIVE);
        if (list.isEmpty()) {
            return List.of();
        }
        Set<Long> userIds = new HashSet<>();
        for (RefundRequest rr : list) {
            if (rr.getRequestedByUserId() != null) userIds.add(rr.getRequestedByUserId());
            if (rr.getReviewedByUserId() != null) userIds.add(rr.getReviewedByUserId());
        }
        Map<Long, User> usersById = userIds.isEmpty()
                ? Map.of()
                : userRepository.findAllById(userIds).stream()
                .collect(Collectors.toMap(User::getId, u -> u, (a, b) -> a));

        return list.stream()
                .map(rr -> toResponse(
                        rr,
                        order,
                        usersById.get(rr.getRequestedByUserId()),
                        rr.getReviewedByUserId() != null ? usersById.get(rr.getReviewedByUserId()) : null
                ))
                .toList();
    }

    @Override
    @Transactional
    public AdminRefundResponse createRefund(String orderGuid, AdminCreateRefundRequest request) {
        User admin = ServiceContext.getUserOrThrow();
        Order order = orderRepository.findOrderByGuid(orderGuid)
                .orElseThrow(() -> new NotFoundException(MessageCd.ORDER_NOT_FOUND));

        BigDecimal orderTotal = nonNegative(order.getOrderTotal());
        BigDecimal alreadyRefunded = sumRefundedForOrder(order.getId(), null);
        BigDecimal remaining = orderTotal.subtract(alreadyRefunded).max(BigDecimal.ZERO);

        BigDecimal requestedAmount;
        if (RefundType.FULL.equals(request.refundType())) {
            requestedAmount = remaining;
        } else {
            requestedAmount = request.amount() != null
                    ? request.amount().setScale(2, RoundingMode.HALF_UP)
                    : BigDecimal.ZERO;
        }

        if (requestedAmount.compareTo(BigDecimal.ZERO) <= 0 || requestedAmount.compareTo(remaining) > 0) {
            throw new BadRequestException(MessageCd.REFUND_INVALID_AMOUNT);
        }

        RefundRequest refundRequest = new RefundRequest();
        refundRequest.setOrderId(order.getId());
        refundRequest.setRequestedByUserId(order.getBuyerId() != null ? order.getBuyerId() : admin.getId());
        refundRequest.setRefundType(request.refundType());
        refundRequest.setRequestedAmount(requestedAmount);
        refundRequest.setRefundStatus(RefundStatus.PENDING);
        refundRequest.setReason(request.reason().trim());
        refundRequest.setAdminNotes(trimToNull(request.adminNotes()));
        refundRequest.setEvidenceUrlsJson("[]");

        RefundRequest saved = refundRequestRepository.save(refundRequest);

        adminAuditService.record(
                AuditAction.REFUND_CREATED,
                "REFUND",
                saved.getGuid(),
                request.reason().trim(),
                null,
                auditSnapshot(saved, order)
        );

        if (request.autoApprove()) {
            return processApproval(saved, order, requestedAmount, trimToNull(request.adminNotes()), admin, alreadyRefunded);
        }

        User requester = userRepository.findById(saved.getRequestedByUserId()).orElse(null);
        return toResponse(saved, order, requester, null);
    }

    @Override
    @Transactional
    public AdminRefundResponse approveRefund(String refundGuid, AdminApproveRefundRequest request) {
        User admin = ServiceContext.getUserOrThrow();
        RefundRequest refund = refundRequestRepository.findByGuidAndStatus(refundGuid, StatusCd.ACTIVE)
                .orElseThrow(() -> new NotFoundException(MessageCd.REFUND_NOT_FOUND));

        if (!RefundStatus.PENDING.equals(refund.getRefundStatus())) {
            throw new ConflictException(MessageCd.REFUND_ALREADY_PROCESSED);
        }

        Order order = orderRepository.findById(refund.getOrderId())
                .orElseThrow(() -> new NotFoundException(MessageCd.ORDER_NOT_FOUND));

        BigDecimal alreadyRefunded = sumRefundedForOrder(order.getId(), refund.getId());
        BigDecimal orderTotal = nonNegative(order.getOrderTotal());
        BigDecimal remaining = orderTotal.subtract(alreadyRefunded).max(BigDecimal.ZERO);

        BigDecimal approvedAmount;
        if (request != null && request.approvedAmount() != null) {
            approvedAmount = request.approvedAmount().setScale(2, RoundingMode.HALF_UP);
        } else {
            approvedAmount = refund.getRequestedAmount() != null
                    ? refund.getRequestedAmount().setScale(2, RoundingMode.HALF_UP)
                    : remaining;
        }

        if (approvedAmount.compareTo(BigDecimal.ZERO) <= 0 || approvedAmount.compareTo(remaining) > 0) {
            throw new BadRequestException(MessageCd.REFUND_INVALID_AMOUNT);
        }

        String adminNotes = request != null ? trimToNull(request.adminNotes()) : refund.getAdminNotes();
        return processApproval(refund, order, approvedAmount, adminNotes, admin, alreadyRefunded);
    }

    @Override
    @Transactional
    public AdminRefundResponse rejectRefund(String refundGuid, AdminRejectRefundRequest request) {
        User admin = ServiceContext.getUserOrThrow();
        RefundRequest refund = refundRequestRepository.findByGuidAndStatus(refundGuid, StatusCd.ACTIVE)
                .orElseThrow(() -> new NotFoundException(MessageCd.REFUND_NOT_FOUND));

        if (!RefundStatus.PENDING.equals(refund.getRefundStatus())) {
            throw new ConflictException(MessageCd.REFUND_ALREADY_PROCESSED);
        }

        Order order = orderRepository.findById(refund.getOrderId()).orElse(null);
        Map<String, Object> before = auditSnapshot(refund, order);
        String reason = request.reason().trim();

        refund.setRefundStatus(RefundStatus.REJECTED);
        refund.setRejectionReason(reason);
        refund.setReviewedByUserId(admin.getId());
        refund.setReviewedAt(Instant.now());

        RefundRequest saved = refundRequestRepository.save(refund);

        adminAuditService.record(
                AuditAction.REFUND_REJECTED,
                "REFUND",
                saved.getGuid(),
                reason,
                before,
                auditSnapshot(saved, order)
        );

        if (eventPublisher != null) {
            eventPublisher.publishEvent(new RefundDecisionNotificationEvent(
                    saved.getGuid(),
                    saved.getOrderId(),
                    saved.getRequestedByUserId(),
                    order != null ? order.getVendorId() : null,
                    false,
                    saved.getRequestedAmount(),
                    reason,
                    saved.getAdminNotes(),
                    saved.getRefundType() != null ? saved.getRefundType().name() : null
            ));
        }

        User requester = userRepository.findById(saved.getRequestedByUserId()).orElse(null);
        return toResponse(saved, order, requester, admin);
    }

    private AdminRefundResponse processApproval(
            RefundRequest refund,
            Order order,
            BigDecimal approvedAmount,
            String adminNotes,
            User admin,
            BigDecimal alreadyRefundedBeforeThis
    ) {
        Map<String, Object> before = auditSnapshot(refund, order);
        String stripeRefundId = executeStripeOrSimulatedRefund(refund, order, approvedAmount);

        refund.setApprovedAmount(approvedAmount);
        refund.setRefundStatus(RefundStatus.PROCESSED);
        refund.setStripeRefundId(stripeRefundId);
        if (adminNotes != null) {
            refund.setAdminNotes(adminNotes);
        }
        refund.setReviewedByUserId(admin.getId());
        refund.setReviewedAt(Instant.now());

        RefundRequest saved = refundRequestRepository.save(refund);

        String orderCode = resolveOrderCode(order);
        if (order.getVendorId() != null) {
            MakerFinanceTransaction tx = new MakerFinanceTransaction();
            tx.setCompanyId(order.getVendorId());
            tx.setOrderId(order.getId());
            tx.setTransactionDate(LocalDate.now(ZoneOffset.UTC));
            tx.setTransactionType(FinanceTransactionType.REFUND);
            tx.setTitle("Reembolso " + saved.getRefundType().name() + " - " + orderCode);
            tx.setReference(saved.getGuid());
            tx.setCategory("REFUND");
            tx.setAmount(approvedAmount.negate());
            tx.setTransactionStatus(FinanceTransactionStatus.COMPLETED);
            tx.setProcessedByUserId(admin.getId());
            tx.setProcessedAt(Instant.now());
            makerFinanceTransactionRepository.save(tx);
        }

        BigDecimal orderTotal = nonNegative(order.getOrderTotal());
        boolean isTotalCovered = RefundType.FULL.equals(saved.getRefundType())
                || alreadyRefundedBeforeThis.add(approvedAmount).compareTo(orderTotal) >= 0;

        if (isTotalCovered) {
            OrderStatusCd currentOrderStatus = orderStatusRepository.findLatestStatusByOrderId(order.getId())
                    .map(OrderStatus::getStatusCd)
                    .orElse(null);
            if (currentOrderStatus != null && orderStatusService.canMoveTo(currentOrderStatus, OrderStatusCd.REFUNDED)) {
                order.setCancellationReason(saved.getReason());
                orderRepository.save(order);
                orderStatusService.move(order, OrderStatusCd.REFUNDED);
            }
        }

        adminAuditService.record(
                AuditAction.REFUND_APPROVED,
                "REFUND",
                saved.getGuid(),
                saved.getReason(),
                before,
                auditSnapshot(saved, order)
        );

        if (eventPublisher != null) {
            eventPublisher.publishEvent(new RefundDecisionNotificationEvent(
                    saved.getGuid(),
                    saved.getOrderId(),
                    saved.getRequestedByUserId(),
                    order.getVendorId(),
                    true,
                    approvedAmount,
                    saved.getReason(),
                    saved.getAdminNotes(),
                    saved.getRefundType() != null ? saved.getRefundType().name() : null
            ));
        }

        User requester = userRepository.findById(saved.getRequestedByUserId()).orElse(null);
        return toResponse(saved, order, requester, admin);
    }

    private String executeStripeOrSimulatedRefund(RefundRequest refund, Order order, BigDecimal amount) {
        String secretKey = stripeConfig.getSecretKey();
        String sessionId = order.getCheckoutSessionId();
        if (secretKey == null || secretKey.isBlank() || sessionId == null || isSimulatedSession(sessionId)) {
            return "re_sim_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        }

        try {
            Stripe.apiKey = secretKey.trim();
            Session session = Session.retrieve(sessionId);
            String paymentIntentId = session.getPaymentIntent();
            if (paymentIntentId == null || paymentIntentId.isBlank()) {
                return "re_sim_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
            }
            long amountInCents = amount.multiply(BigDecimal.valueOf(100))
                    .setScale(0, RoundingMode.HALF_UP)
                    .longValueExact();
            RefundCreateParams params = RefundCreateParams.builder()
                    .setPaymentIntent(paymentIntentId)
                    .setAmount(amountInCents)
                    .putMetadata("orderGuid", order.getGuid())
                    .putMetadata("refundGuid", refund.getGuid())
                    .build();
            Refund stripeRefund = Refund.create(params);
            return stripeRefund.getId();
        } catch (Exception ex) {
            log.error("Falha ao processar reembolso na Stripe para pedido [{}]: {}", order.getGuid(), ex.getMessage());
            throw new BadGatewayException(MessageCd.BAD_GATEWAY);
        }
    }

    private boolean isSimulatedSession(String sessionId) {
        return sessionId.startsWith("cs_test_sim_")
                || sessionId.startsWith("cs_test_local_")
                || sessionId.startsWith("cs_cart_")
                || sessionId.startsWith("cs_quote_sim_");
    }

    private BigDecimal sumRefundedForOrder(Long orderId, Long excludeRefundId) {
        return refundRequestRepository.findAllByOrderIdAndStatusOrderByCreateDtDesc(orderId, StatusCd.ACTIVE)
                .stream()
                .filter(rr -> excludeRefundId == null || !excludeRefundId.equals(rr.getId()))
                .filter(rr -> SETTLED_REFUND_STATUSES.contains(rr.getRefundStatus()))
                .map(rr -> rr.getApprovedAmount() != null ? rr.getApprovedAmount() : rr.getRequestedAmount())
                .filter(java.util.Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private AdminRefundResponse toResponse(RefundRequest rr, Order order, User requester, User reviewer) {
        String orderCode = order != null ? resolveOrderCode(order) : "N/A";
        return new AdminRefundResponse(
                rr.getGuid(),
                order != null ? order.getGuid() : null,
                orderCode,
                order != null ? nonNegative(order.getOrderTotal()) : BigDecimal.ZERO,
                requester != null ? requester.getGuid() : null,
                requester != null ? requester.getFullName() : "Solicitante",
                requester != null ? requester.getEmail() : null,
                rr.getRefundType(),
                rr.getRequestedAmount(),
                rr.getApprovedAmount(),
                rr.getRefundStatus(),
                rr.getReason(),
                jsonCodec.readStringList(rr.getEvidenceUrlsJson()),
                rr.getAdminNotes(),
                rr.getRejectionReason(),
                rr.getStripeRefundId(),
                reviewer != null ? reviewer.getFullName() : null,
                rr.getReviewedAt(),
                rr.getCreateDt()
        );
    }

    private boolean matchesQuery(AdminRefundResponse resp, String query) {
        if (query == null) {
            return true;
        }
        String q = query.toLowerCase(Locale.ROOT);
        return contains(resp.guid(), q)
                || contains(resp.orderCode(), q)
                || contains(resp.orderGuid(), q)
                || contains(resp.requestedByName(), q)
                || contains(resp.requestedByEmail(), q)
                || contains(resp.reason(), q);
    }

    private boolean contains(String field, String needle) {
        return field != null && field.toLowerCase(Locale.ROOT).contains(needle);
    }

    private String resolveOrderCode(Order order) {
        if (order.getOrderCode() != null && !order.getOrderCode().isBlank()) {
            return order.getOrderCode();
        }
        return orderViewAssembler.formatOrderCode(order.getGuid());
    }

    private Map<String, Object> auditSnapshot(RefundRequest rr, Order order) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("guid", rr.getGuid());
        if (order != null) {
            map.put("orderGuid", order.getGuid());
        }
        map.put("refundType", rr.getRefundType() != null ? rr.getRefundType().name() : null);
        map.put("refundStatus", rr.getRefundStatus() != null ? rr.getRefundStatus().name() : null);
        map.put("requestedAmount", rr.getRequestedAmount());
        if (rr.getApprovedAmount() != null) {
            map.put("approvedAmount", rr.getApprovedAmount());
        }
        if (rr.getStripeRefundId() != null) {
            map.put("stripeRefundId", rr.getStripeRefundId());
        }
        return map;
    }

    private BigDecimal nonNegative(BigDecimal val) {
        if (val == null || val.compareTo(BigDecimal.ZERO) < 0) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        return val.setScale(2, RoundingMode.HALF_UP);
    }

    private String trimToNull(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
