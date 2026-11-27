package org.imprime.ai.api.service.impl;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.imprime.ai.api.http.ServiceContext;
import org.imprime.ai.api.http.request.admin.AdminPayoutCompleteRequest;
import org.imprime.ai.api.http.request.admin.AdminPayoutFailRequest;
import org.imprime.ai.api.http.response.PageResponse;
import org.imprime.ai.api.http.response.admin.AdminEscrowItemResponse;
import org.imprime.ai.api.http.response.admin.AdminFinanceSummaryResponse;
import org.imprime.ai.api.http.response.admin.AdminPayoutItemResponse;
import org.imprime.ai.api.model.Company;
import org.imprime.ai.api.model.DesignerFinanceTransaction;
import org.imprime.ai.api.model.MakerFinanceTransaction;
import org.imprime.ai.api.model.Order;
import org.imprime.ai.api.model.OrderStatus;
import org.imprime.ai.api.model.RefundRequest;
import org.imprime.ai.api.model.SupportTicket;
import org.imprime.ai.api.model.User;
import org.imprime.ai.api.model.enums.AdminPayoutBeneficiaryType;
import org.imprime.ai.api.model.enums.AuditAction;
import org.imprime.ai.api.model.enums.FinanceTransactionStatus;
import org.imprime.ai.api.model.enums.FinanceTransactionType;
import org.imprime.ai.api.model.enums.MessageCd;
import org.imprime.ai.api.model.enums.OrderStatusCd;
import org.imprime.ai.api.model.enums.RefundStatus;
import org.imprime.ai.api.model.enums.StatusCd;
import org.imprime.ai.api.model.enums.SupportTicketStatus;
import org.imprime.ai.api.model.exception.BadRequestException;
import org.imprime.ai.api.model.exception.ConflictException;
import org.imprime.ai.api.model.exception.NotFoundException;
import org.imprime.ai.api.notification.event.PayoutNotificationEvent;
import org.imprime.ai.api.repo.db.CompanyRepository;
import org.imprime.ai.api.repo.db.DesignerFinanceTransactionRepository;
import org.imprime.ai.api.repo.db.MakerFinanceTransactionRepository;
import org.imprime.ai.api.repo.db.OrderRepository;
import org.imprime.ai.api.repo.db.OrderStatusRepository;
import org.imprime.ai.api.repo.db.RefundRequestRepository;
import org.imprime.ai.api.repo.db.SupportTicketRepository;
import org.imprime.ai.api.repo.db.UserRepository;
import org.imprime.ai.api.service.AdminAuditService;
import org.imprime.ai.api.service.AdminFinanceService;
import org.imprime.ai.api.service.OrderViewAssembler;
import org.imprime.ai.api.util.PageUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AdminFinanceServiceImpl implements AdminFinanceService {
    private static final Set<OrderStatusCd> GMV_ELIGIBLE_STATUSES = Set.of(
            OrderStatusCd.CONFIRMED,
            OrderStatusCd.IN_PRODUCTION,
            OrderStatusCd.READY_TO_SHIP,
            OrderStatusCd.IN_TRANSIT,
            OrderStatusCd.COMPLETED
    );

    private static final Set<OrderStatusCd> ESCROW_PROGRESS_STATUSES = Set.of(
            OrderStatusCd.CONFIRMED,
            OrderStatusCd.IN_PRODUCTION,
            OrderStatusCd.READY_TO_SHIP,
            OrderStatusCd.IN_TRANSIT
    );

    private static final Set<OrderStatusCd> NON_ESCROW_TERMINAL_STATUSES = Set.of(
            OrderStatusCd.CREATED,
            OrderStatusCd.QUOTING,
            OrderStatusCd.CANCELLED,
            OrderStatusCd.ERROR,
            OrderStatusCd.REFUNDED
    );

    private static final Set<SupportTicketStatus> OPEN_TICKET_STATUSES = Set.of(
            SupportTicketStatus.OPEN,
            SupportTicketStatus.IN_REVIEW,
            SupportTicketStatus.WAITING_USER
    );

    private static final Set<RefundStatus> SETTLED_REFUND_STATUSES = Set.of(
            RefundStatus.APPROVED,
            RefundStatus.PROCESSED
    );

    private static final Set<FinanceTransactionStatus> ACTIONABLE_PAYOUT_STATUSES = Set.of(
            FinanceTransactionStatus.PENDING,
            FinanceTransactionStatus.PROCESSING
    );

    private static final Comparator<AdminPayoutItemResponse> PAYOUT_ORDER = Comparator
            .comparing(AdminPayoutItemResponse::transactionDate, Comparator.nullsLast(Comparator.reverseOrder()))
            .thenComparing(AdminPayoutItemResponse::createdAt, Comparator.nullsLast(Comparator.reverseOrder()));

    private final MakerFinanceTransactionRepository makerFinanceTransactionRepository;
    private final DesignerFinanceTransactionRepository designerFinanceTransactionRepository;
    private final OrderRepository orderRepository;
    private final OrderStatusRepository orderStatusRepository;
    private final UserRepository userRepository;
    private final CompanyRepository companyRepository;
    private final RefundRequestRepository refundRequestRepository;
    private final SupportTicketRepository supportTicketRepository;
    private final OrderViewAssembler orderViewAssembler;
    private final AdminAuditService adminAuditService;
    @Autowired(required = false)
    private ApplicationEventPublisher eventPublisher;

    @Value("${imprime.ai.finance.platform-fee-rate:0.10}")
    private BigDecimal platformFeeRate = new BigDecimal("0.10");

    @Override
    public AdminFinanceSummaryResponse getSummary() {
        FinanceOrderSnapshot snapshot = buildOrderAndEscrowSnapshot();

        List<MakerFinanceTransaction> makerPayouts = makerFinanceTransactionRepository
                .findAllByTransactionTypeAndStatusOrderByTransactionDateDescCreateDtDesc(
                        FinanceTransactionType.PAYOUT,
                        StatusCd.ACTIVE
                );
        List<DesignerFinanceTransaction> designerPayouts = designerFinanceTransactionRepository
                .findAllByTransactionTypeAndStatusOrderByTransactionDateDescCreateDtDesc(
                        FinanceTransactionType.PAYOUT,
                        StatusCd.ACTIVE
                );

        PayoutTotals makerTotals = sumPayoutTotals(
                makerPayouts.stream().map(tx -> new PayoutAmountView(tx.getAmount(), tx.getTransactionStatus())).toList()
        );
        PayoutTotals designerTotals = sumPayoutTotals(
                designerPayouts.stream().map(tx -> new PayoutAmountView(tx.getAmount(), tx.getTransactionStatus())).toList()
        );

        BigDecimal totalPaid = makerTotals.paid().add(designerTotals.paid()).setScale(2, RoundingMode.HALF_UP);
        BigDecimal totalPending = makerTotals.pending().add(designerTotals.pending()).setScale(2, RoundingMode.HALF_UP);
        long pendingCount = makerTotals.pendingCount() + designerTotals.pendingCount();

        BigDecimal netPlatformRevenue = snapshot.platformFeeRevenue()
                .subtract(snapshot.partialRefundFeeImpact())
                .max(BigDecimal.ZERO)
                .setScale(2, RoundingMode.HALF_UP);

        return new AdminFinanceSummaryResponse(
                snapshot.gmvTotal(),
                snapshot.platformFeeRevenue(),
                snapshot.escrowBalance(),
                snapshot.escrowItems().size(),
                makerTotals.paid(),
                makerTotals.pending(),
                designerTotals.paid(),
                designerTotals.pending(),
                totalPaid,
                totalPending,
                pendingCount,
                snapshot.refundsTotal(),
                netPlatformRevenue
        );
    }

    @Override
    public PageResponse<AdminPayoutItemResponse> listPayouts(
            AdminPayoutBeneficiaryType beneficiaryType,
            FinanceTransactionStatus status,
            LocalDate fromDate,
            LocalDate toDate,
            String q,
            Pageable pageable
    ) {
        if (fromDate != null && toDate != null && fromDate.isAfter(toDate)) {
            throw new BadRequestException(MessageCd.VALIDATION_ERROR);
        }

        List<MakerFinanceTransaction> makerTxs = beneficiaryType == AdminPayoutBeneficiaryType.DESIGNER
                ? List.of()
                : makerFinanceTransactionRepository
                .findAllByTransactionTypeAndStatusOrderByTransactionDateDescCreateDtDesc(
                        FinanceTransactionType.PAYOUT,
                        StatusCd.ACTIVE
                );

        List<DesignerFinanceTransaction> designerTxs = beneficiaryType == AdminPayoutBeneficiaryType.MAKER
                ? List.of()
                : designerFinanceTransactionRepository
                .findAllByTransactionTypeAndStatusOrderByTransactionDateDescCreateDtDesc(
                        FinanceTransactionType.PAYOUT,
                        StatusCd.ACTIVE
                );

        Set<Long> companyIds = new HashSet<>();
        Set<Long> userIds = new HashSet<>();
        for (MakerFinanceTransaction tx : makerTxs) {
            if (tx.getCompanyId() != null) companyIds.add(tx.getCompanyId());
            if (tx.getProcessedByUserId() != null) userIds.add(tx.getProcessedByUserId());
        }
        Map<Long, Company> companiesById = companyIds.isEmpty()
                ? Map.of()
                : companyRepository.findAllByIdIn(companyIds).stream()
                .collect(Collectors.toMap(Company::getId, c -> c, (a, b) -> a));
        for (Company company : companiesById.values()) {
            if (company.getOwnerId() != null) userIds.add(company.getOwnerId());
        }
        for (DesignerFinanceTransaction tx : designerTxs) {
            if (tx.getDesignerUserId() != null) userIds.add(tx.getDesignerUserId());
            if (tx.getProcessedByUserId() != null) userIds.add(tx.getProcessedByUserId());
        }

        Map<Long, User> usersById = userIds.isEmpty()
                ? Map.of()
                : userRepository.findAllById(userIds).stream()
                .collect(Collectors.toMap(User::getId, u -> u, (a, b) -> a));

        List<AdminPayoutItemResponse> items = new ArrayList<>(makerTxs.size() + designerTxs.size());
        String normalizedQuery = trimToNull(q);

        for (MakerFinanceTransaction tx : makerTxs) {
            AdminPayoutItemResponse dto = toMakerPayoutItem(tx, companiesById, usersById);
            if (matchesPayoutFilters(dto, status, fromDate, toDate, normalizedQuery)) {
                items.add(dto);
            }
        }
        for (DesignerFinanceTransaction tx : designerTxs) {
            AdminPayoutItemResponse dto = toDesignerPayoutItem(tx, usersById);
            if (matchesPayoutFilters(dto, status, fromDate, toDate, normalizedQuery)) {
                items.add(dto);
            }
        }

        items.sort(PAYOUT_ORDER);
        return PageUtils.paginate(items, pageable);
    }

    @Override
    @Transactional
    public AdminPayoutItemResponse completePayout(String payoutGuid, AdminPayoutCompleteRequest request) {
        User admin = ServiceContext.getUserOrThrow();
        Instant now = Instant.now();
        String newReference = request != null ? trimToNull(request.reference()) : null;
        String notes = request != null ? trimToNull(request.notes()) : null;
        String auditReason = notes != null ? notes : "Repasse marcado como concluído";

        Optional<MakerFinanceTransaction> makerOpt = makerFinanceTransactionRepository.findByGuidAndStatus(payoutGuid, StatusCd.ACTIVE);
        if (makerOpt.isPresent()) {
            MakerFinanceTransaction tx = makerOpt.get();
            validateActionablePayout(tx.getTransactionType(), tx.getTransactionStatus());

            Map<String, Object> beforeState = snapshotPayout(tx.getGuid(), AdminPayoutBeneficiaryType.MAKER, tx.getTransactionStatus(), tx.getReference());
            tx.setTransactionStatus(FinanceTransactionStatus.COMPLETED);
            tx.setProcessedByUserId(admin.getId());
            tx.setProcessedAt(now);
            tx.setFailureReason(null);
            if (newReference != null) {
                tx.setReference(newReference);
            }
            MakerFinanceTransaction saved = makerFinanceTransactionRepository.save(tx);

            adminAuditService.record(
                    AuditAction.PAYOUT_COMPLETED,
                    "PAYOUT",
                    saved.getGuid(),
                    auditReason,
                    beforeState,
                    snapshotPayout(saved.getGuid(), AdminPayoutBeneficiaryType.MAKER, saved.getTransactionStatus(), saved.getReference())
            );
            publishMakerPayoutEvent(saved, "COMPLETED", null);
            return hydrateMakerPayout(saved);
        }

        DesignerFinanceTransaction tx = designerFinanceTransactionRepository.findByGuidAndStatus(payoutGuid, StatusCd.ACTIVE)
                .orElseThrow(() -> new NotFoundException(MessageCd.PAYOUT_NOT_FOUND));
        validateActionablePayout(tx.getTransactionType(), tx.getTransactionStatus());

        Map<String, Object> beforeState = snapshotPayout(tx.getGuid(), AdminPayoutBeneficiaryType.DESIGNER, tx.getTransactionStatus(), tx.getReference());
        tx.setTransactionStatus(FinanceTransactionStatus.COMPLETED);
        tx.setProcessedByUserId(admin.getId());
        tx.setProcessedAt(now);
        tx.setFailureReason(null);
        if (newReference != null) {
            tx.setReference(newReference);
        }
        DesignerFinanceTransaction saved = designerFinanceTransactionRepository.save(tx);

        adminAuditService.record(
                AuditAction.PAYOUT_COMPLETED,
                "PAYOUT",
                saved.getGuid(),
                auditReason,
                beforeState,
                snapshotPayout(saved.getGuid(), AdminPayoutBeneficiaryType.DESIGNER, saved.getTransactionStatus(), saved.getReference())
        );
        publishDesignerPayoutEvent(saved, "COMPLETED", null);
        return hydrateDesignerPayout(saved);
    }

    @Override
    @Transactional
    public AdminPayoutItemResponse failPayout(String payoutGuid, AdminPayoutFailRequest request) {
        User admin = ServiceContext.getUserOrThrow();
        Instant now = Instant.now();
        String reason = request.reason().trim();

        Optional<MakerFinanceTransaction> makerOpt = makerFinanceTransactionRepository.findByGuidAndStatus(payoutGuid, StatusCd.ACTIVE);
        if (makerOpt.isPresent()) {
            MakerFinanceTransaction tx = makerOpt.get();
            validateActionablePayout(tx.getTransactionType(), tx.getTransactionStatus());

            Map<String, Object> beforeState = snapshotPayout(tx.getGuid(), AdminPayoutBeneficiaryType.MAKER, tx.getTransactionStatus(), tx.getReference());
            tx.setTransactionStatus(FinanceTransactionStatus.FAILED);
            tx.setProcessedByUserId(admin.getId());
            tx.setProcessedAt(now);
            tx.setFailureReason(reason);
            MakerFinanceTransaction saved = makerFinanceTransactionRepository.save(tx);

            adminAuditService.record(
                    AuditAction.PAYOUT_FAILED,
                    "PAYOUT",
                    saved.getGuid(),
                    reason,
                    beforeState,
                    snapshotPayout(saved.getGuid(), AdminPayoutBeneficiaryType.MAKER, saved.getTransactionStatus(), saved.getReference())
            );
            publishMakerPayoutEvent(saved, "FAILED", reason);
            return hydrateMakerPayout(saved);
        }

        DesignerFinanceTransaction tx = designerFinanceTransactionRepository.findByGuidAndStatus(payoutGuid, StatusCd.ACTIVE)
                .orElseThrow(() -> new NotFoundException(MessageCd.PAYOUT_NOT_FOUND));
        validateActionablePayout(tx.getTransactionType(), tx.getTransactionStatus());

        Map<String, Object> beforeState = snapshotPayout(tx.getGuid(), AdminPayoutBeneficiaryType.DESIGNER, tx.getTransactionStatus(), tx.getReference());
        tx.setTransactionStatus(FinanceTransactionStatus.FAILED);
        tx.setProcessedByUserId(admin.getId());
        tx.setProcessedAt(now);
        tx.setFailureReason(reason);
        DesignerFinanceTransaction saved = designerFinanceTransactionRepository.save(tx);

        adminAuditService.record(
                AuditAction.PAYOUT_FAILED,
                "PAYOUT",
                saved.getGuid(),
                reason,
                beforeState,
                snapshotPayout(saved.getGuid(), AdminPayoutBeneficiaryType.DESIGNER, saved.getTransactionStatus(), saved.getReference())
        );
        publishDesignerPayoutEvent(saved, "FAILED", reason);
        return hydrateDesignerPayout(saved);
    }

    private void publishMakerPayoutEvent(MakerFinanceTransaction tx, String status, String failureReason) {
        if (eventPublisher == null || tx == null) {
            return;
        }
        BigDecimal absAmount = tx.getAmount() != null ? tx.getAmount().abs() : BigDecimal.ZERO;
        eventPublisher.publishEvent(new PayoutNotificationEvent(
                tx.getGuid(),
                "MAKER",
                tx.getCompanyId(),
                null,
                status,
                absAmount,
                tx.getPixKeyMasked(),
                tx.getReference(),
                failureReason
        ));
    }

    private void publishDesignerPayoutEvent(DesignerFinanceTransaction tx, String status, String failureReason) {
        if (eventPublisher == null || tx == null) {
            return;
        }
        BigDecimal absAmount = tx.getAmount() != null ? tx.getAmount().abs() : BigDecimal.ZERO;
        eventPublisher.publishEvent(new PayoutNotificationEvent(
                tx.getGuid(),
                "DESIGNER",
                null,
                tx.getDesignerUserId(),
                status,
                absAmount,
                tx.getPixKeyMasked(),
                tx.getReference(),
                failureReason
        ));
    }

    @Override
    public PageResponse<AdminEscrowItemResponse> listEscrow(
            String q,
            OrderStatusCd orderStatus,
            Boolean disputedOnly,
            Pageable pageable
    ) {
        FinanceOrderSnapshot snapshot = buildOrderAndEscrowSnapshot();
        String normalizedQuery = trimToNull(q);

        List<AdminEscrowItemResponse> filtered = snapshot.escrowItems().stream()
                .filter(item -> orderStatus == null || orderStatus.equals(item.orderStatus()))
                .filter(item -> !Boolean.TRUE.equals(disputedOnly) || item.hasOpenDispute())
                .filter(item -> matchesEscrowQuery(item, normalizedQuery))
                .toList();

        return PageUtils.paginate(filtered, pageable);
    }

    private FinanceOrderSnapshot buildOrderAndEscrowSnapshot() {
        List<Order> orders = orderRepository.findAllByStatusOrderByCreateDtDesc(StatusCd.ACTIVE);
        List<MakerFinanceTransaction> feeTransactions = makerFinanceTransactionRepository
                .findAllByTransactionTypeAndStatusOrderByTransactionDateDescCreateDtDesc(
                        FinanceTransactionType.PLATFORM_FEE,
                        StatusCd.ACTIVE
                );

        Map<Long, BigDecimal> explicitFeeByOrderId = new HashMap<>();
        BigDecimal standaloneFeeTotal = BigDecimal.ZERO;
        for (MakerFinanceTransaction tx : feeTransactions) {
            if (tx == null || FinanceTransactionStatus.CANCELLED.equals(tx.getTransactionStatus())
                    || FinanceTransactionStatus.FAILED.equals(tx.getTransactionStatus())) {
                continue;
            }
            BigDecimal absAmount = absMoney(tx.getAmount());
            if (tx.getOrderId() != null) {
                explicitFeeByOrderId.merge(tx.getOrderId(), absAmount, BigDecimal::add);
            } else {
                standaloneFeeTotal = standaloneFeeTotal.add(absAmount);
            }
        }

        List<SupportTicket> activeTickets = supportTicketRepository.findAllByStatusOrderByUpdateDtDescCreateDtDesc(StatusCd.ACTIVE);
        Set<Long> openDisputedOrderIds = new HashSet<>();
        for (SupportTicket ticket : activeTickets) {
            if (ticket != null
                    && Boolean.TRUE.equals(ticket.getIsDispute())
                    && ticket.getOrderId() != null
                    && OPEN_TICKET_STATUSES.contains(ticket.getTicketStatus())) {
                openDisputedOrderIds.add(ticket.getOrderId());
            }
        }

        List<RefundRequest> activeRefunds = refundRequestRepository.findAllByStatusOrderByCreateDtDesc(StatusCd.ACTIVE);
        Set<Long> pendingRefundOrderIds = new HashSet<>();
        Map<Long, BigDecimal> settledRefundByOrderId = new HashMap<>();
        BigDecimal refundsTotal = BigDecimal.ZERO;
        for (RefundRequest rr : activeRefunds) {
            if (rr == null) {
                continue;
            }
            if (RefundStatus.PENDING.equals(rr.getRefundStatus()) && rr.getOrderId() != null) {
                pendingRefundOrderIds.add(rr.getOrderId());
            }
            if (SETTLED_REFUND_STATUSES.contains(rr.getRefundStatus())) {
                BigDecimal settledAmount = rr.getApprovedAmount() != null ? rr.getApprovedAmount() : rr.getRequestedAmount();
                BigDecimal positiveAmount = absMoney(settledAmount);
                refundsTotal = refundsTotal.add(positiveAmount);
                if (rr.getOrderId() != null) {
                    settledRefundByOrderId.merge(rr.getOrderId(), positiveAmount, BigDecimal::add);
                }
            }
        }

        Set<Long> buyerIds = new HashSet<>();
        Set<Long> vendorIds = new HashSet<>();
        for (Order order : orders) {
            if (order.getBuyerId() != null) buyerIds.add(order.getBuyerId());
            if (order.getVendorId() != null) vendorIds.add(order.getVendorId());
        }

        Map<Long, User> buyersById = buyerIds.isEmpty()
                ? Map.of()
                : userRepository.findAllById(buyerIds).stream()
                .collect(Collectors.toMap(User::getId, u -> u, (a, b) -> a));

        Map<Long, Company> companiesById = vendorIds.isEmpty()
                ? Map.of()
                : companyRepository.findAllByIdIn(vendorIds).stream()
                .collect(Collectors.toMap(Company::getId, c -> c, (a, b) -> a));

        BigDecimal gmvTotal = BigDecimal.ZERO;
        BigDecimal feeTotal = standaloneFeeTotal;
        BigDecimal escrowBalance = BigDecimal.ZERO;
        BigDecimal partialRefundFeeImpact = BigDecimal.ZERO;
        BigDecimal feeRate = effectiveFeeRate();
        List<AdminEscrowItemResponse> escrowItems = new ArrayList<>();

        for (Order order : orders) {
            OrderStatusCd statusCd = resolveLatestStatusCd(order);
            BigDecimal orderGross = nonNegative(order.getOrderTotal());
            BigDecimal explicitFee = order.getId() != null ? explicitFeeByOrderId.get(order.getId()) : null;
            BigDecimal orderFee = explicitFee != null
                    ? explicitFee.setScale(2, RoundingMode.HALF_UP)
                    : orderGross.multiply(feeRate).setScale(2, RoundingMode.HALF_UP);

            if (GMV_ELIGIBLE_STATUSES.contains(statusCd)) {
                gmvTotal = gmvTotal.add(orderGross);
                feeTotal = feeTotal.add(orderFee);
                BigDecimal orderSettledRefund = order.getId() != null
                        ? settledRefundByOrderId.getOrDefault(order.getId(), BigDecimal.ZERO)
                        : BigDecimal.ZERO;
                if (orderSettledRefund.compareTo(BigDecimal.ZERO) > 0) {
                    partialRefundFeeImpact = partialRefundFeeImpact.add(
                            orderSettledRefund.multiply(feeRate).setScale(2, RoundingMode.HALF_UP)
                    );
                }
            }

            boolean hasOpenDispute = order.getId() != null && openDisputedOrderIds.contains(order.getId());
            boolean hasPendingRefund = order.getId() != null && pendingRefundOrderIds.contains(order.getId());
            boolean heldInEscrow = !NON_ESCROW_TERMINAL_STATUSES.contains(statusCd)
                    && (ESCROW_PROGRESS_STATUSES.contains(statusCd) || hasOpenDispute || hasPendingRefund);

            if (heldInEscrow) {
                BigDecimal refundedAmount = order.getId() != null
                        ? settledRefundByOrderId.getOrDefault(order.getId(), BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP)
                        : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
                BigDecimal netHeld = orderGross.subtract(orderFee).subtract(refundedAmount)
                        .max(BigDecimal.ZERO)
                        .setScale(2, RoundingMode.HALF_UP);
                escrowBalance = escrowBalance.add(netHeld);

                User buyer = order.getBuyerId() != null ? buyersById.get(order.getBuyerId()) : null;
                Company maker = order.getVendorId() != null ? companiesById.get(order.getVendorId()) : null;
                String orderCode = (order.getOrderCode() != null && !order.getOrderCode().isBlank())
                        ? order.getOrderCode()
                        : orderViewAssembler.formatOrderCode(order.getGuid());
                String holdReason = hasOpenDispute
                        ? "DISPUTE_OPEN"
                        : (hasPendingRefund ? "REFUND_PENDING" : "ORDER_IN_PROGRESS");

                escrowItems.add(new AdminEscrowItemResponse(
                        order.getGuid(),
                        orderCode,
                        statusCd,
                        buyer != null ? buyer.getGuid() : null,
                        buyer != null ? buyer.getFullName() : "Comprador",
                        maker != null ? maker.getGuid() : null,
                        maker != null ? maker.getName() : null,
                        orderGross,
                        orderFee,
                        refundedAmount,
                        netHeld,
                        holdReason,
                        hasOpenDispute,
                        hasPendingRefund,
                        order.getCreateDt(),
                        order.getUpdateDt()
                ));
            }
        }

        return new FinanceOrderSnapshot(
                gmvTotal.setScale(2, RoundingMode.HALF_UP),
                feeTotal.setScale(2, RoundingMode.HALF_UP),
                escrowBalance.setScale(2, RoundingMode.HALF_UP),
                refundsTotal.setScale(2, RoundingMode.HALF_UP),
                partialRefundFeeImpact.setScale(2, RoundingMode.HALF_UP),
                escrowItems
        );
    }

    private PayoutTotals sumPayoutTotals(List<PayoutAmountView> views) {
        BigDecimal paid = BigDecimal.ZERO;
        BigDecimal pending = BigDecimal.ZERO;
        long pendingCount = 0L;
        for (PayoutAmountView view : views) {
            BigDecimal amount = absMoney(view.amount());
            if (FinanceTransactionStatus.COMPLETED.equals(view.status())) {
                paid = paid.add(amount);
            } else if (ACTIONABLE_PAYOUT_STATUSES.contains(view.status())) {
                pending = pending.add(amount);
                pendingCount++;
            }
        }
        return new PayoutTotals(
                paid.setScale(2, RoundingMode.HALF_UP),
                pending.setScale(2, RoundingMode.HALF_UP),
                pendingCount
        );
    }

    private AdminPayoutItemResponse hydrateMakerPayout(MakerFinanceTransaction tx) {
        Map<Long, Company> companiesById = tx.getCompanyId() == null
                ? Map.of()
                : companyRepository.findById(tx.getCompanyId())
                .map(c -> Map.of(c.getId(), c))
                .orElse(Map.of());
        Set<Long> userIds = new HashSet<>();
        companiesById.values().forEach(c -> {
            if (c.getOwnerId() != null) userIds.add(c.getOwnerId());
        });
        if (tx.getProcessedByUserId() != null) userIds.add(tx.getProcessedByUserId());
        Map<Long, User> usersById = userIds.isEmpty()
                ? Map.of()
                : userRepository.findAllById(userIds).stream()
                .collect(Collectors.toMap(User::getId, u -> u, (a, b) -> a));
        return toMakerPayoutItem(tx, companiesById, usersById);
    }

    private AdminPayoutItemResponse hydrateDesignerPayout(DesignerFinanceTransaction tx) {
        Set<Long> userIds = new HashSet<>();
        if (tx.getDesignerUserId() != null) userIds.add(tx.getDesignerUserId());
        if (tx.getProcessedByUserId() != null) userIds.add(tx.getProcessedByUserId());
        Map<Long, User> usersById = userIds.isEmpty()
                ? Map.of()
                : userRepository.findAllById(userIds).stream()
                .collect(Collectors.toMap(User::getId, u -> u, (a, b) -> a));
        return toDesignerPayoutItem(tx, usersById);
    }

    private AdminPayoutItemResponse toMakerPayoutItem(
            MakerFinanceTransaction tx,
            Map<Long, Company> companiesById,
            Map<Long, User> usersById
    ) {
        Company company = tx.getCompanyId() != null ? companiesById.get(tx.getCompanyId()) : null;
        User owner = (company != null && company.getOwnerId() != null) ? usersById.get(company.getOwnerId()) : null;
        User processor = tx.getProcessedByUserId() != null ? usersById.get(tx.getProcessedByUserId()) : null;

        return new AdminPayoutItemResponse(
                tx.getGuid(),
                AdminPayoutBeneficiaryType.MAKER,
                company != null ? company.getGuid() : null,
                company != null ? company.getName() : "Maker",
                owner != null ? owner.getEmail() : null,
                absMoney(tx.getAmount()),
                tx.getTransactionStatus(),
                tx.getTransactionDate(),
                tx.getTitle(),
                tx.getReference(),
                tx.getPixKeyType(),
                tx.getPixKeyMasked(),
                tx.getFailureReason(),
                processor != null ? processor.getGuid() : null,
                processor != null ? processor.getFullName() : null,
                tx.getProcessedAt(),
                tx.getCreateDt()
        );
    }

    private AdminPayoutItemResponse toDesignerPayoutItem(
            DesignerFinanceTransaction tx,
            Map<Long, User> usersById
    ) {
        User designer = tx.getDesignerUserId() != null ? usersById.get(tx.getDesignerUserId()) : null;
        User processor = tx.getProcessedByUserId() != null ? usersById.get(tx.getProcessedByUserId()) : null;

        return new AdminPayoutItemResponse(
                tx.getGuid(),
                AdminPayoutBeneficiaryType.DESIGNER,
                designer != null ? designer.getGuid() : null,
                designer != null ? designer.getFullName() : "Designer",
                designer != null ? designer.getEmail() : null,
                absMoney(tx.getAmount()),
                tx.getTransactionStatus(),
                tx.getTransactionDate(),
                tx.getTitle(),
                tx.getReference(),
                tx.getPixKeyType(),
                tx.getPixKeyMasked(),
                tx.getFailureReason(),
                processor != null ? processor.getGuid() : null,
                processor != null ? processor.getFullName() : null,
                tx.getProcessedAt(),
                tx.getCreateDt()
        );
    }

    private void validateActionablePayout(FinanceTransactionType type, FinanceTransactionStatus status) {
        if (!FinanceTransactionType.PAYOUT.equals(type) || !ACTIONABLE_PAYOUT_STATUSES.contains(status)) {
            throw new ConflictException(MessageCd.PAYOUT_INVALID_STATE);
        }
    }

    private Map<String, Object> snapshotPayout(
            String guid,
            AdminPayoutBeneficiaryType type,
            FinanceTransactionStatus status,
            String reference
    ) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("payoutGuid", guid);
        snapshot.put("beneficiaryType", type.name());
        snapshot.put("status", status != null ? status.name() : null);
        snapshot.put("reference", reference);
        return snapshot;
    }

    private boolean matchesPayoutFilters(
            AdminPayoutItemResponse item,
            FinanceTransactionStatus status,
            LocalDate fromDate,
            LocalDate toDate,
            String query
    ) {
        if (status != null && !status.equals(item.status())) {
            return false;
        }
        LocalDate effectiveDate = item.transactionDate() != null
                ? item.transactionDate()
                : (item.createdAt() != null ? item.createdAt().atOffset(ZoneOffset.UTC).toLocalDate() : null);
        if (fromDate != null && (effectiveDate == null || effectiveDate.isBefore(fromDate))) {
            return false;
        }
        if (toDate != null && (effectiveDate == null || effectiveDate.isAfter(toDate))) {
            return false;
        }
        if (query == null) {
            return true;
        }
        String q = query.toLowerCase(Locale.ROOT);
        return contains(item.payoutGuid(), q)
                || contains(item.beneficiaryName(), q)
                || contains(item.beneficiaryEmail(), q)
                || contains(item.title(), q)
                || contains(item.reference(), q)
                || contains(item.pixKeyMasked(), q);
    }

    private boolean matchesEscrowQuery(AdminEscrowItemResponse item, String query) {
        if (query == null) {
            return true;
        }
        String q = query.toLowerCase(Locale.ROOT);
        return contains(item.orderCode(), q)
                || contains(item.orderGuid(), q)
                || contains(item.buyerName(), q)
                || contains(item.makerName(), q)
                || contains(item.holdReason(), q);
    }

    private boolean contains(String field, String needle) {
        return field != null && field.toLowerCase(Locale.ROOT).contains(needle);
    }

    private OrderStatusCd resolveLatestStatusCd(Order order) {
        if (order.getId() != null) {
            return orderStatusRepository.findLatestStatusByOrderId(order.getId())
                    .map(OrderStatus::getStatusCd)
                    .orElse(OrderStatusCd.CREATED);
        }
        return OrderStatusCd.CREATED;
    }

    private BigDecimal effectiveFeeRate() {
        if (platformFeeRate == null || platformFeeRate.compareTo(BigDecimal.ZERO) < 0) {
            return new BigDecimal("0.10");
        }
        return platformFeeRate;
    }

    private BigDecimal absMoney(BigDecimal value) {
        if (value == null) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        return value.abs().setScale(2, RoundingMode.HALF_UP);
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

    private record PayoutAmountView(BigDecimal amount, FinanceTransactionStatus status) {
    }

    private record PayoutTotals(BigDecimal paid, BigDecimal pending, long pendingCount) {
    }

    private record FinanceOrderSnapshot(
            BigDecimal gmvTotal,
            BigDecimal platformFeeRevenue,
            BigDecimal escrowBalance,
            BigDecimal refundsTotal,
            BigDecimal partialRefundFeeImpact,
            List<AdminEscrowItemResponse> escrowItems
    ) {
    }
}
