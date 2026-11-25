package org.imprime.ai.api.service.impl;

import lombok.RequiredArgsConstructor;
import org.imprime.ai.api.http.response.PageResponse;
import org.imprime.ai.api.http.response.admin.AdminDashboardSummaryResponse;
import org.imprime.ai.api.http.response.admin.AdminSlaAlertResponse;
import org.imprime.ai.api.http.response.admin.DashboardSeriesPointResponse;
import org.imprime.ai.api.model.Company;
import org.imprime.ai.api.model.MakerFinanceTransaction;
import org.imprime.ai.api.model.Model3D;
import org.imprime.ai.api.model.Order;
import org.imprime.ai.api.model.OrderItem;
import org.imprime.ai.api.model.OrderStatus;
import org.imprime.ai.api.model.SupportTicket;
import org.imprime.ai.api.model.User;
import org.imprime.ai.api.model.enums.AdminOrderType;
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
import org.imprime.ai.api.service.AdminDashboardService;
import org.imprime.ai.api.service.AdminOrderSlaHelper;
import org.imprime.ai.api.service.OrderViewAssembler;
import org.imprime.ai.api.util.PageUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AdminDashboardServiceImpl implements AdminDashboardService {
    private static final Set<OrderStatusCd> GMV_ELIGIBLE_STATUSES = Set.of(
            OrderStatusCd.CONFIRMED,
            OrderStatusCd.IN_PRODUCTION,
            OrderStatusCd.READY_TO_SHIP,
            OrderStatusCd.IN_TRANSIT,
            OrderStatusCd.COMPLETED
    );

    private static final Set<OrderStatusCd> IN_PROGRESS_STATUSES = Set.of(
            OrderStatusCd.CONFIRMED,
            OrderStatusCd.IN_PRODUCTION,
            OrderStatusCd.READY_TO_SHIP,
            OrderStatusCd.IN_TRANSIT
    );

    private static final Set<SupportTicketStatus> OPEN_TICKET_STATUSES = Set.of(
            SupportTicketStatus.OPEN,
            SupportTicketStatus.IN_REVIEW,
            SupportTicketStatus.WAITING_USER
    );

    private final OrderRepository orderRepository;
    private final OrderStatusRepository orderStatusRepository;
    private final OrderItemRepository orderItemRepository;
    private final UserRepository userRepository;
    private final CompanyRepository companyRepository;
    private final Model3DRepository model3DRepository;
    private final SupportTicketRepository supportTicketRepository;
    private final ContentReportRepository contentReportRepository;
    private final RefundRequestRepository refundRequestRepository;
    private final MakerFinanceTransactionRepository financeTransactionRepository;
    private final AdminOrderSlaHelper slaHelper;
    private final OrderViewAssembler orderViewAssembler;

    @Value("${imprime.ai.finance.platform-fee-rate:0.10}")
    private BigDecimal platformFeeRate = new BigDecimal("0.10");

    @Override
    public AdminDashboardSummaryResponse getSummary(String range) {
        String normalizedRange = normalizeRange(range);
        Instant now = Instant.now();
        Instant periodStart = resolvePeriodStart(normalizedRange, now);

        List<Order> orders = orderRepository.findAllByStatusOrderByCreateDtDesc(StatusCd.ACTIVE);
        List<MakerFinanceTransaction> feeTransactions = financeTransactionRepository
                .findAllByTransactionTypeAndStatusOrderByTransactionDateDescCreateDtDesc(
                        FinanceTransactionType.PLATFORM_FEE,
                        StatusCd.ACTIVE
                );

        Map<Long, BigDecimal> explicitFeeByOrderId = new HashMap<>();
        BigDecimal standaloneFeeTotal = BigDecimal.ZERO;
        BigDecimal standaloneFeePeriod = BigDecimal.ZERO;
        for (MakerFinanceTransaction tx : feeTransactions) {
            if (tx == null || FinanceTransactionStatus.CANCELLED.equals(tx.getTransactionStatus())
                    || FinanceTransactionStatus.FAILED.equals(tx.getTransactionStatus())) {
                continue;
            }
            BigDecimal absAmount = tx.getAmount() != null ? tx.getAmount().abs() : BigDecimal.ZERO;
            if (tx.getOrderId() != null) {
                explicitFeeByOrderId.merge(tx.getOrderId(), absAmount, BigDecimal::add);
            } else {
                standaloneFeeTotal = standaloneFeeTotal.add(absAmount);
                Instant txInstant = tx.getCreateDt() != null
                        ? tx.getCreateDt()
                        : (tx.getTransactionDate() != null ? tx.getTransactionDate().atStartOfDay().toInstant(ZoneOffset.UTC) : null);
                if (isInPeriod(txInstant, periodStart)) {
                    standaloneFeePeriod = standaloneFeePeriod.add(absAmount);
                }
            }
        }

        Set<Long> vendorIds = orders.stream()
                .map(Order::getVendorId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Long, Company> companiesById = vendorIds.isEmpty()
                ? Map.of()
                : companyRepository.findAllByIdIn(vendorIds).stream()
                .collect(Collectors.toMap(Company::getId, c -> c, (a, b) -> a));

        BigDecimal gmvTotal = BigDecimal.ZERO;
        BigDecimal gmvPeriod = BigDecimal.ZERO;
        BigDecimal feeTotal = standaloneFeeTotal;
        BigDecimal feePeriod = standaloneFeePeriod;
        long ordersInProgress = 0L;
        long ordersCompletedPeriod = 0L;
        long delayedSlaCount = 0L;

        Map<String, Long> ordersByStatus = new LinkedHashMap<>();
        for (OrderStatusCd statusCd : OrderStatusCd.values()) {
            ordersByStatus.put(statusCd.name(), 0L);
        }

        Map<LocalDate, SeriesAccumulator> seriesByDay = new LinkedHashMap<>();
        List<Order> gmvOrdersForSeries = new ArrayList<>();

        for (Order order : orders) {
            OrderStatus latest = resolveLatestStatus(order);
            OrderStatusCd statusCd = latest.getStatusCd();
            ordersByStatus.merge(statusCd.name(), 1L, Long::sum);

            if (IN_PROGRESS_STATUSES.contains(statusCd)) {
                ordersInProgress++;
            }

            boolean inPeriod = isInPeriod(order.getCreateDt(), periodStart);
            if (OrderStatusCd.COMPLETED.equals(statusCd) && inPeriod) {
                ordersCompletedPeriod++;
            }

            if (GMV_ELIGIBLE_STATUSES.contains(statusCd)) {
                BigDecimal orderTotal = nonNegative(order.getOrderTotal());
                BigDecimal explicitFee = explicitFeeByOrderId.get(order.getId());
                BigDecimal orderFee = explicitFee != null
                        ? explicitFee
                        : orderTotal.multiply(effectiveFeeRate()).setScale(2, RoundingMode.HALF_UP);

                gmvTotal = gmvTotal.add(orderTotal);
                feeTotal = feeTotal.add(orderFee);

                if (inPeriod) {
                    gmvPeriod = gmvPeriod.add(orderTotal);
                    feePeriod = feePeriod.add(orderFee);
                    gmvOrdersForSeries.add(order);
                    LocalDate bucketDate = toUtcDate(order.getCreateDt(), now);
                    seriesByDay.computeIfAbsent(bucketDate, k -> new SeriesAccumulator())
                            .add(orderTotal, orderFee);
                }
            }

            Company vendor = order.getVendorId() != null ? companiesById.get(order.getVendorId()) : null;
            if (slaHelper.evaluateSla(order, latest, vendor, now).delayed()) {
                delayedSlaCount++;
            }
        }

        List<SupportTicket> activeTickets = supportTicketRepository.findAllByStatusOrderByUpdateDtDescCreateDtDesc(StatusCd.ACTIVE);
        long openTickets = activeTickets.stream()
                .filter(t -> OPEN_TICKET_STATUSES.contains(t.getTicketStatus()))
                .count();
        long openDisputes = activeTickets.stream()
                .filter(t -> Boolean.TRUE.equals(t.getIsDispute()) && OPEN_TICKET_STATUSES.contains(t.getTicketStatus()))
                .count();
        long openReports = contentReportRepository.countByReportStatusInAndStatus(
                List.of(ReportStatus.OPEN, ReportStatus.UNDER_REVIEW),
                StatusCd.ACTIVE
        );

        long pendingModels = model3DRepository.findAllByStatus(StatusCd.ACTIVE).stream()
                .filter(m -> ModelModerationStatus.PENDING_REVIEW.equals(m.getModerationStatus()))
                .count();

        List<Company> activeCompanies = companyRepository.findAllByStatus(StatusCd.ACTIVE);
        long pendingMakers = activeCompanies.stream()
                .filter(c -> MakerApprovalStatus.PENDING_APPROVAL.equals(c.getApprovalStatus()))
                .count();
        long activeMakers = activeCompanies.stream()
                .filter(c -> c.getApprovalStatus() == null || MakerApprovalStatus.APPROVED.equals(c.getApprovalStatus()))
                .count();

        long pendingRefunds = refundRequestRepository.countByRefundStatusAndStatus(RefundStatus.PENDING, StatusCd.ACTIVE);
        long totalUsers = userRepository.findAllByStatus(StatusCd.ACTIVE).size();

        List<DashboardSeriesPointResponse> series = buildSeries(normalizedRange, seriesByDay, now);

        return new AdminDashboardSummaryResponse(
                normalizedRange,
                gmvTotal.setScale(2, RoundingMode.HALF_UP),
                gmvPeriod.setScale(2, RoundingMode.HALF_UP),
                feeTotal.setScale(2, RoundingMode.HALF_UP),
                feePeriod.setScale(2, RoundingMode.HALF_UP),
                ordersInProgress,
                ordersCompletedPeriod,
                openTickets,
                openDisputes,
                openReports,
                pendingModels,
                pendingMakers,
                pendingRefunds,
                delayedSlaCount,
                totalUsers,
                activeMakers,
                series,
                ordersByStatus
        );
    }

    @Override
    public PageResponse<AdminSlaAlertResponse> listSlaAlerts(String severity, Pageable pageable) {
        Instant now = Instant.now();
        String normalizedSeverity = severity != null && !severity.isBlank()
                ? severity.trim().toUpperCase(Locale.ROOT)
                : null;

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

        List<AdminSlaAlertResponse> alerts = new ArrayList<>();
        for (Order order : orders) {
            OrderStatus latest = resolveLatestStatus(order);
            Company vendor = order.getVendorId() != null ? companiesById.get(order.getVendorId()) : null;
            AdminOrderSlaHelper.SlaEvaluation eval = slaHelper.evaluateSla(order, latest, vendor, now);
            if (!eval.delayed()) {
                continue;
            }
            if (normalizedSeverity != null && !normalizedSeverity.equalsIgnoreCase(eval.severity())) {
                continue;
            }

            User buyer = order.getBuyerId() != null ? buyersById.get(order.getBuyerId()) : null;
            String buyerName = buyer != null ? buyer.getFullName() : "Comprador";
            String makerName = vendor != null ? vendor.getName() : "N/A";
            String orderCode = (order.getOrderCode() != null && !order.getOrderCode().isBlank())
                    ? order.getOrderCode()
                    : orderViewAssembler.formatOrderCode(order.getGuid());
            AdminOrderType orderType = slaHelper.classifyOrderType(order, itemsByOrderId.get(order.getId()));

            alerts.add(new AdminSlaAlertResponse(
                    order.getGuid(),
                    orderCode,
                    latest.getStatusCd(),
                    orderType,
                    eval.severity(),
                    eval.delayHours(),
                    eval.expectedDeadlineAt(),
                    eval.reason(),
                    buyerName,
                    makerName,
                    nonNegative(order.getOrderTotal()),
                    order.getCreateDt()
            ));
        }

        alerts.sort(
                Comparator.comparing((AdminSlaAlertResponse a) -> "CRITICAL".equals(a.severity()) ? 0 : 1)
                        .thenComparing(Comparator.comparingLong(AdminSlaAlertResponse::delayHours).reversed())
        );

        return PageUtils.paginate(alerts, pageable);
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

    private String normalizeRange(String range) {
        if (range == null || range.isBlank()) {
            return "30d";
        }
        return switch (range.trim().toLowerCase(Locale.ROOT)) {
            case "7d", "30d", "90d", "all" -> range.trim().toLowerCase(Locale.ROOT);
            default -> "30d";
        };
    }

    private Instant resolvePeriodStart(String normalizedRange, Instant now) {
        return switch (normalizedRange) {
            case "7d" -> now.minus(7, ChronoUnit.DAYS);
            case "30d" -> now.minus(30, ChronoUnit.DAYS);
            case "90d" -> now.minus(90, ChronoUnit.DAYS);
            default -> null;
        };
    }

    private boolean isInPeriod(Instant instant, Instant periodStart) {
        if (periodStart == null) {
            return true;
        }
        return instant != null && !instant.isBefore(periodStart);
    }

    private LocalDate toUtcDate(Instant instant, Instant fallback) {
        Instant target = instant != null ? instant : fallback;
        return target.atOffset(ZoneOffset.UTC).toLocalDate();
    }

    private List<DashboardSeriesPointResponse> buildSeries(
            String normalizedRange,
            Map<LocalDate, SeriesAccumulator> seriesByDay,
            Instant now
    ) {
        if ("7d".equals(normalizedRange) || "30d".equals(normalizedRange)) {
            int days = "7d".equals(normalizedRange) ? 7 : 30;
            LocalDate today = now.atOffset(ZoneOffset.UTC).toLocalDate();
            List<DashboardSeriesPointResponse> points = new ArrayList<>(days);
            for (int i = days - 1; i >= 0; i--) {
                LocalDate day = today.minusDays(i);
                SeriesAccumulator acc = seriesByDay.getOrDefault(day, SeriesAccumulator.EMPTY);
                points.add(new DashboardSeriesPointResponse(
                        day.toString(),
                        acc.gmv.setScale(2, RoundingMode.HALF_UP),
                        acc.fee.setScale(2, RoundingMode.HALF_UP),
                        acc.count
                ));
            }
            return points;
        }

        return seriesByDay.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> new DashboardSeriesPointResponse(
                        entry.getKey().toString(),
                        entry.getValue().gmv.setScale(2, RoundingMode.HALF_UP),
                        entry.getValue().fee.setScale(2, RoundingMode.HALF_UP),
                        entry.getValue().count
                ))
                .toList();
    }

    private BigDecimal effectiveFeeRate() {
        return platformFeeRate != null ? platformFeeRate : new BigDecimal("0.10");
    }

    private BigDecimal nonNegative(BigDecimal value) {
        if (value == null || value.compareTo(BigDecimal.ZERO) < 0) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        return value.setScale(2, RoundingMode.HALF_UP);
    }

    private static final class SeriesAccumulator {
        private static final SeriesAccumulator EMPTY = new SeriesAccumulator();
        private BigDecimal gmv = BigDecimal.ZERO;
        private BigDecimal fee = BigDecimal.ZERO;
        private long count = 0L;

        void add(BigDecimal orderGmv, BigDecimal orderFee) {
            this.gmv = this.gmv.add(orderGmv);
            this.fee = this.fee.add(orderFee);
            this.count++;
        }
    }
}
