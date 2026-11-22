package org.imprime.ai.api.service.impl;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.imprime.ai.api.http.request.admin.AdminCancelOrderRequest;
import org.imprime.ai.api.http.request.admin.AdminCreateRefundRequest;
import org.imprime.ai.api.http.response.PageResponse;
import org.imprime.ai.api.http.response.admin.AdminOrderDetailResponse;
import org.imprime.ai.api.http.response.admin.AdminOrderSummaryResponse;
import org.imprime.ai.api.http.response.admin.AdminRefundResponse;
import org.imprime.ai.api.http.response.order.OrderDetailResponse;
import org.imprime.ai.api.model.Company;
import org.imprime.ai.api.model.Order;
import org.imprime.ai.api.model.OrderItem;
import org.imprime.ai.api.model.OrderStatus;
import org.imprime.ai.api.model.User;
import org.imprime.ai.api.model.enums.AdminOrderType;
import org.imprime.ai.api.model.enums.AuditAction;
import org.imprime.ai.api.model.enums.MessageCd;
import org.imprime.ai.api.model.enums.OrderStatusCd;
import org.imprime.ai.api.model.enums.RefundType;
import org.imprime.ai.api.model.enums.StatusCd;
import org.imprime.ai.api.model.exception.BadRequestException;
import org.imprime.ai.api.model.exception.ConflictException;
import org.imprime.ai.api.model.exception.NotFoundException;
import org.imprime.ai.api.repo.db.CompanyRepository;
import org.imprime.ai.api.repo.db.OrderItemRepository;
import org.imprime.ai.api.repo.db.OrderRepository;
import org.imprime.ai.api.repo.db.OrderStatusRepository;
import org.imprime.ai.api.repo.db.UserRepository;
import org.imprime.ai.api.service.AdminAuditService;
import org.imprime.ai.api.service.AdminOrderService;
import org.imprime.ai.api.service.AdminOrderSlaHelper;
import org.imprime.ai.api.service.AdminRefundService;
import org.imprime.ai.api.service.OrderStatusService;
import org.imprime.ai.api.service.OrderViewAssembler;
import org.imprime.ai.api.util.PageUtils;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AdminOrderServiceImpl implements AdminOrderService {
    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final OrderStatusRepository orderStatusRepository;
    private final UserRepository userRepository;
    private final CompanyRepository companyRepository;
    private final OrderStatusService orderStatusService;
    private final OrderViewAssembler orderViewAssembler;
    private final AdminOrderSlaHelper slaHelper;
    private final AdminRefundService adminRefundService;
    private final AdminAuditService adminAuditService;

    @Override
    public PageResponse<AdminOrderSummaryResponse> listOrders(
            String q,
            OrderStatusCd status,
            AdminOrderType orderType,
            Boolean slaDelayedOnly,
            LocalDate fromDate,
            LocalDate toDate,
            Pageable pageable
    ) {
        if (fromDate != null && toDate != null && fromDate.isAfter(toDate)) {
            throw new BadRequestException(MessageCd.VALIDATION_ERROR);
        }

        List<Order> orders = orderRepository.findAllByStatusOrderByCreateDtDesc(StatusCd.ACTIVE);
        if (orders.isEmpty()) {
            return PageUtils.paginate(List.of(), pageable);
        }

        Set<Long> orderIds = new HashSet<>();
        Set<Long> buyerIds = new HashSet<>();
        Set<Long> vendorIds = new HashSet<>();
        for (Order order : orders) {
            if (order.getId() != null) orderIds.add(order.getId());
            if (order.getBuyerId() != null) buyerIds.add(order.getBuyerId());
            if (order.getVendorId() != null) vendorIds.add(order.getVendorId());
        }

        Map<Long, List<OrderItem>> itemsByOrderId = orderIds.isEmpty()
                ? Map.of()
                : orderItemRepository.findAllByOrderIdInAndStatus(orderIds, StatusCd.ACTIVE).stream()
                .collect(Collectors.groupingBy(OrderItem::getOrderId));

        Map<Long, User> buyersById = buyerIds.isEmpty()
                ? Map.of()
                : userRepository.findAllById(buyerIds).stream()
                .collect(Collectors.toMap(User::getId, u -> u, (a, b) -> a));

        Map<Long, Company> companiesById = vendorIds.isEmpty()
                ? Map.of()
                : companyRepository.findAllByIdIn(vendorIds).stream()
                .collect(Collectors.toMap(Company::getId, c -> c, (a, b) -> a));

        Instant now = Instant.now();
        String normalizedQuery = trimToNull(q);

        List<AdminOrderSummaryResponse> results = new ArrayList<>();
        for (Order order : orders) {
            if (!matchesDateRange(order, fromDate, toDate)) {
                continue;
            }
            OrderStatus latestStatus = resolveLatestStatus(order);
            if (status != null && !status.equals(latestStatus.getStatusCd())) {
                continue;
            }

            List<OrderItem> items = itemsByOrderId.getOrDefault(order.getId(), List.of());
            AdminOrderType resolvedType = slaHelper.classifyOrderType(order, items);
            if (orderType != null && !orderType.equals(resolvedType)) {
                continue;
            }

            Company vendor = order.getVendorId() != null ? companiesById.get(order.getVendorId()) : null;
            AdminOrderSlaHelper.SlaEvaluation sla = slaHelper.evaluateSla(order, latestStatus, vendor, now);
            if (Boolean.TRUE.equals(slaDelayedOnly) && !sla.delayed()) {
                continue;
            }

            User buyer = order.getBuyerId() != null ? buyersById.get(order.getBuyerId()) : null;
            AdminOrderSummaryResponse summary = toSummary(order, latestStatus.getStatusCd(), resolvedType, items, buyer, vendor, sla);
            if (matchesQuery(summary, normalizedQuery)) {
                results.add(summary);
            }
        }

        return PageUtils.paginate(results, pageable);
    }

    @Override
    public AdminOrderDetailResponse getOrderDetail(String orderGuid) {
        Order order = requireOrder(orderGuid);
        return buildAdminDetail(order);
    }

    @Override
    @Transactional
    public AdminOrderDetailResponse cancelOrder(String orderGuid, AdminCancelOrderRequest request) {
        Order order = requireOrder(orderGuid);
        OrderStatus latestStatus = resolveLatestStatus(order);
        OrderStatusCd current = latestStatus.getStatusCd();

        if (OrderStatusCd.CANCELLED.equals(current) || OrderStatusCd.REFUNDED.equals(current)
                || !orderStatusService.canMoveTo(current, OrderStatusCd.CANCELLED)) {
            throw new ConflictException(MessageCd.INVALID_ORDER_STATUS_TRANSITION);
        }

        String reason = request.reason().trim();
        Map<String, Object> before = Map.of(
                "orderGuid", order.getGuid(),
                "status", current.name()
        );

        order.setCancellationReason(reason);
        orderRepository.save(order);
        orderStatusService.move(order, OrderStatusCd.CANCELLED);

        adminAuditService.record(
                AuditAction.ORDER_CANCELLED,
                "ORDER",
                order.getGuid(),
                reason,
                before,
                Map.of(
                        "orderGuid", order.getGuid(),
                        "status", OrderStatusCd.CANCELLED.name(),
                        "initiateFullRefund", request.initiateFullRefund()
                )
        );

        if (request.initiateFullRefund()) {
            BigDecimal total = nonNegative(order.getOrderTotal());
            if (total.compareTo(BigDecimal.ZERO) > 0) {
                adminRefundService.createRefund(
                        orderGuid,
                        new AdminCreateRefundRequest(
                                RefundType.FULL,
                                total,
                                reason,
                                "Reembolso automático por cancelamento administrativo",
                                true
                        )
                );
            }
        }

        return buildAdminDetail(order);
    }

    private AdminOrderDetailResponse buildAdminDetail(Order order) {
        OrderDetailResponse detail = orderViewAssembler.toDetailResponse(order);
        List<OrderItem> items = order.getId() != null
                ? orderItemRepository.findAllByOrderIdAndStatusOrderByIdAsc(order.getId(), StatusCd.ACTIVE)
                : List.of();
        AdminOrderType orderType = slaHelper.classifyOrderType(order, items);
        OrderStatus latest = resolveLatestStatus(order);
        Company vendor = order.getVendorId() != null
                ? companyRepository.findById(order.getVendorId()).orElse(null)
                : null;
        AdminOrderSlaHelper.SlaEvaluation sla = slaHelper.evaluateSla(order, latest, vendor, Instant.now());
        List<AdminRefundResponse> refunds = adminRefundService.listRefundsForOrder(order.getId());

        return new AdminOrderDetailResponse(
                detail,
                orderType,
                order.getCheckoutSessionId(),
                order.getCancellationReason(),
                sla.delayed(),
                sla.severity(),
                sla.delayHours(),
                sla.expectedDeadlineAt(),
                sla.reason(),
                refunds
        );
    }

    private AdminOrderSummaryResponse toSummary(
            Order order,
            OrderStatusCd statusCd,
            AdminOrderType orderType,
            List<OrderItem> items,
            User buyer,
            Company vendor,
            AdminOrderSlaHelper.SlaEvaluation sla
    ) {
        String orderCode = (order.getOrderCode() != null && !order.getOrderCode().isBlank())
                ? order.getOrderCode()
                : orderViewAssembler.formatOrderCode(order.getGuid());
        BigDecimal shipping = nonNegative(order.getShippingCost());
        BigDecimal total = nonNegative(order.getOrderTotal());
        BigDecimal subtotal = total.compareTo(shipping) >= 0 ? total.subtract(shipping) : total;

        return new AdminOrderSummaryResponse(
                order.getGuid(),
                orderCode,
                statusCd,
                orderType,
                subtotal,
                shipping,
                total,
                buyer != null ? buyer.getGuid() : null,
                buyer != null ? buyer.getFullName() : "Comprador",
                buyer != null ? buyer.getEmail() : null,
                vendor != null ? vendor.getGuid() : null,
                vendor != null ? vendor.getName() : null,
                items.isEmpty() ? (order.getFileAssetId() != null ? 1 : 0) : items.size(),
                order.getTrackingCode(),
                sla.delayed(),
                sla.severity(),
                sla.delayHours(),
                sla.reason(),
                order.getCreateDt(),
                order.getUpdateDt()
        );
    }

    private Order requireOrder(String orderGuid) {
        return orderRepository.findOrderByGuid(orderGuid)
                .orElseThrow(() -> new NotFoundException(MessageCd.ORDER_NOT_FOUND));
    }

    private OrderStatus resolveLatestStatus(Order order) {
        if (order.getId() != null) {
            var opt = orderStatusRepository.findLatestStatusByOrderId(order.getId());
            if (opt.isPresent()) {
                return opt.get();
            }
        }
        OrderStatus fallback = new OrderStatus();
        fallback.setOrderId(order.getId());
        fallback.setStatusCd(OrderStatusCd.CREATED);
        fallback.setStatusDate(order.getCreateDt());
        return fallback;
    }

    private boolean matchesDateRange(Order order, LocalDate fromDate, LocalDate toDate) {
        if (fromDate == null && toDate == null) {
            return true;
        }
        if (order.getCreateDt() == null) {
            return false;
        }
        LocalDate created = order.getCreateDt().atOffset(ZoneOffset.UTC).toLocalDate();
        if (fromDate != null && created.isBefore(fromDate)) {
            return false;
        }
        return toDate == null || !created.isAfter(toDate);
    }

    private boolean matchesQuery(AdminOrderSummaryResponse summary, String query) {
        if (query == null) {
            return true;
        }
        String q = query.toLowerCase(Locale.ROOT);
        return contains(summary.orderCode(), q)
                || contains(summary.orderGuid(), q)
                || contains(summary.buyerName(), q)
                || contains(summary.buyerEmail(), q)
                || contains(summary.makerName(), q)
                || contains(summary.trackingCode(), q);
    }

    private boolean contains(String field, String needle) {
        return field != null && field.toLowerCase(Locale.ROOT).contains(needle);
    }

    private BigDecimal nonNegative(BigDecimal value) {
        if (value == null || value.compareTo(BigDecimal.ZERO) < 0) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        return value.setScale(2, RoundingMode.HALF_UP);
    }

    private String trimToNull(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
