package org.imprime.ai.api.service.impl;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.imprime.ai.api.http.request.maker.CreateExpenseRequest;
import org.imprime.ai.api.http.request.maker.CreatePayoutRequest;
import org.imprime.ai.api.http.request.maker.UpdateFinanceRatesRequest;
import org.imprime.ai.api.http.response.PageResponse;
import org.imprime.ai.api.http.response.maker.CostBreakdownDto;
import org.imprime.ai.api.http.response.maker.FinanceRatesResponse;
import org.imprime.ai.api.http.response.maker.FinanceTransactionResponse;
import org.imprime.ai.api.http.response.maker.MakerFinanceSummaryResponse;
import org.imprime.ai.api.http.response.maker.MonthlyFinanceSeriesDto;
import org.imprime.ai.api.http.response.maker.PayoutResponse;
import org.imprime.ai.api.model.Asset;
import org.imprime.ai.api.model.Company;
import org.imprime.ai.api.model.Filament;
import org.imprime.ai.api.model.MakerFinanceTransaction;
import org.imprime.ai.api.model.MakerQuoteSettings;
import org.imprime.ai.api.model.Order;
import org.imprime.ai.api.model.OrderStatus;
import org.imprime.ai.api.model.PrintQueueItem;
import org.imprime.ai.api.model.QuoteSettings;
import org.imprime.ai.api.model.enums.CostCategory;
import org.imprime.ai.api.model.enums.ExpenseCategory;
import org.imprime.ai.api.model.enums.FinanceTransactionStatus;
import org.imprime.ai.api.model.enums.FinanceTransactionType;
import org.imprime.ai.api.model.enums.MessageCd;
import org.imprime.ai.api.model.enums.OrderStatusCd;
import org.imprime.ai.api.model.enums.PixKeyType;
import org.imprime.ai.api.model.enums.PrintJobStatus;
import org.imprime.ai.api.model.enums.StatusCd;
import org.imprime.ai.api.model.exception.BadRequestException;
import org.imprime.ai.api.model.exception.ConflictException;
import org.imprime.ai.api.repo.cache.MakerQuoteSettingsInMemoryCache;
import org.imprime.ai.api.repo.cache.QuoteSettingsInMemoryCache;
import org.imprime.ai.api.repo.db.AssetRepository;
import org.imprime.ai.api.repo.db.CompanyRepository;
import org.imprime.ai.api.repo.db.FilamentRepository;
import org.imprime.ai.api.repo.db.MakerFinanceTransactionRepository;
import org.imprime.ai.api.repo.db.MakerQuoteSettingsRepository;
import org.imprime.ai.api.repo.db.OrderRepository;
import org.imprime.ai.api.repo.db.PrintQueueItemRepository;
import org.imprime.ai.api.repo.db.QuoteSettingsRepository;
import org.imprime.ai.api.service.MakerCompanyResolver;
import org.imprime.ai.api.service.MakerFinanceService;
import org.imprime.ai.api.service.OrderStatusService;
import org.imprime.ai.api.util.JsonCodec;
import org.imprime.ai.api.util.MaskingUtil;
import org.imprime.ai.api.util.PageUtils;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class MakerFinanceServiceImpl implements MakerFinanceService {
    public static final BigDecimal DEFAULT_KWH_RATE = new BigDecimal("0.9200");
    public static final BigDecimal DEFAULT_HOURLY_DEPRECIATION_RATE = new BigDecimal("2.50");
    private static final int DEFAULT_POWER_WATTS = 300;
    private static final int MONTHLY_SERIES_MONTHS = 6;
    private static final BigDecimal ZERO_MONEY = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
    private static final BigDecimal ONE_HUNDRED = new BigDecimal("100");
    private static final BigDecimal MINUTES_PER_HOUR = new BigDecimal("60");
    private static final BigDecimal GRAMS_PER_KG = new BigDecimal("1000");
    private static final BigDecimal WATTS_PER_KW = new BigDecimal("1000");

    private static final Set<OrderStatusCd> PENDING_ORDER_STATUSES = Set.of(
            OrderStatusCd.CONFIRMED,
            OrderStatusCd.IN_PRODUCTION,
            OrderStatusCd.READY_TO_SHIP,
            OrderStatusCd.IN_TRANSIT
    );

    private final MakerCompanyResolver makerCompanyResolver;
    private final MakerFinanceTransactionRepository financeTransactionRepository;
    private final OrderRepository orderRepository;
    private final OrderStatusService orderStatusService;
    private final CompanyRepository companyRepository;
    private final AssetRepository assetRepository;
    private final PrintQueueItemRepository printQueueItemRepository;
    private final FilamentRepository filamentRepository;
    private final QuoteSettingsInMemoryCache quoteSettingsInMemoryCache;
    private final QuoteSettingsRepository quoteSettingsRepository;
    private final MakerQuoteSettingsRepository makerQuoteSettingsRepository;
    private final MakerQuoteSettingsInMemoryCache makerQuoteSettingsInMemoryCache;
    private final JsonCodec jsonCodec;

    @Override
    public MakerFinanceSummaryResponse getSummary() {
        Company company = makerCompanyResolver.requireActiveMakerCompany();
        YearMonth currentMonth = YearMonth.now(ZoneOffset.UTC);
        LedgerCalculation ledger = calculateLedger(company, currentMonth, true);

        BigDecimal monthlyRevenue = ledger.revenueByMonth().getOrDefault(currentMonth, BigDecimal.ZERO)
                .setScale(2, RoundingMode.HALF_UP);
        BigDecimal monthlyExpenses = ledger.expensesByMonth().getOrDefault(currentMonth, BigDecimal.ZERO)
                .setScale(2, RoundingMode.HALF_UP);
        BigDecimal netProfit = monthlyRevenue.subtract(monthlyExpenses).setScale(2, RoundingMode.HALF_UP);
        BigDecimal profitMarginPercent = monthlyRevenue.compareTo(BigDecimal.ZERO) > 0
                ? netProfit.multiply(ONE_HUNDRED).divide(monthlyRevenue, 2, RoundingMode.HALF_UP)
                : ZERO_MONEY;

        List<CostBreakdownDto> costBreakdown = buildCostBreakdown(ledger.currentMonthCostByCategory());
        List<MonthlyFinanceSeriesDto> monthlySeries = buildMonthlySeries(
                currentMonth, ledger.revenueByMonth(), ledger.expensesByMonth()
        );

        return new MakerFinanceSummaryResponse(
                ledger.availableBalance(),
                ledger.pendingBalance(),
                monthlyRevenue,
                monthlyExpenses,
                netProfit,
                profitMarginPercent,
                MaskingUtil.maskPixKey(company.getPixKeyType(), company.getPixKey()),
                company.getPixKeyType(),
                resolveKwhRate(company),
                resolveDepreciationRate(company),
                costBreakdown,
                monthlySeries
        );
    }

    @Override
    public PageResponse<FinanceTransactionResponse> listTransactions(
            FinanceTransactionType type,
            FinanceTransactionStatus status,
            LocalDate fromDate,
            LocalDate toDate,
            Pageable pageable) {
        if (fromDate != null && toDate != null && fromDate.isAfter(toDate)) {
            throw new BadRequestException(MessageCd.VALIDATION_ERROR);
        }

        Company company = makerCompanyResolver.requireActiveMakerCompany();
        List<MakerFinanceTransaction> all = financeTransactionRepository
                .findAllByCompanyIdAndStatusOrderByTransactionDateDescCreateDtDesc(company.getId(), StatusCd.ACTIVE);

        Comparator<MakerFinanceTransaction> comparator = buildTransactionComparator(pageable);

        List<FinanceTransactionResponse> filtered = all.stream()
                .filter(tx -> type == null || type.equals(tx.getTransactionType()))
                .filter(tx -> status == null || status.equals(tx.getTransactionStatus()))
                .filter(tx -> fromDate == null
                        || (tx.getTransactionDate() != null && !tx.getTransactionDate().isBefore(fromDate)))
                .filter(tx -> toDate == null
                        || (tx.getTransactionDate() != null && !tx.getTransactionDate().isAfter(toDate)))
                .sorted(comparator)
                .map(this::toTransactionResponse)
                .toList();

        return PageUtils.paginate(filtered, pageable);
    }

    @Override
    @Transactional
    public PayoutResponse requestPayout(String idempotencyKey, CreatePayoutRequest request) {
        Company company = makerCompanyResolver.requireActiveMakerCompany();
        String normalizedIdemKey = trimToNull(idempotencyKey);
        BigDecimal requestedAmount = request.amount().setScale(2, RoundingMode.HALF_UP);
        String maskedPix = MaskingUtil.maskPixKey(request.pixKeyType(), request.pixKey());

        if (normalizedIdemKey != null) {
            Optional<MakerFinanceTransaction> existing = financeTransactionRepository
                    .findByIdempotencyKeyAndStatus(normalizedIdemKey, StatusCd.ACTIVE);
            if (existing.isPresent()) {
                return resolveIdempotentPayout(
                        existing.get(), company, requestedAmount, request.pixKeyType(), maskedPix
                );
            }
        }

        BalanceSnapshot balance = computeBalanceSnapshot(company);
        if (requestedAmount.compareTo(balance.availableBalance()) > 0) {
            throw new BadRequestException(MessageCd.INSUFFICIENT_BALANCE);
        }

        MakerFinanceTransaction payout = new MakerFinanceTransaction();
        payout.setCompanyId(company.getId());
        payout.setTransactionDate(LocalDate.now(ZoneOffset.UTC));
        payout.setTransactionType(FinanceTransactionType.PAYOUT);
        payout.setTitle("Saque PIX (" + request.pixKeyType().name() + ")");
        payout.setReference(normalizedIdemKey != null ? normalizedIdemKey : request.pixKeyType().name());
        payout.setCategory("PIX");
        payout.setAmount(requestedAmount.negate());
        payout.setTransactionStatus(FinanceTransactionStatus.PROCESSING);
        payout.setPixKeyType(request.pixKeyType());
        payout.setPixKeyMasked(maskedPix);
        payout.setIdempotencyKey(normalizedIdemKey);

        MakerFinanceTransaction saved;
        try {
            saved = financeTransactionRepository.save(payout);
        } catch (DataIntegrityViolationException ex) {
            if (normalizedIdemKey != null) {
                Optional<MakerFinanceTransaction> concurrent = financeTransactionRepository
                        .findByIdempotencyKeyAndStatus(normalizedIdemKey, StatusCd.ACTIVE);
                if (concurrent.isPresent()) {
                    return resolveIdempotentPayout(
                            concurrent.get(), company, requestedAmount, request.pixKeyType(), maskedPix
                    );
                }
            }
            throw new ConflictException(MessageCd.CONFLICT);
        }

        BigDecimal remaining = balance.availableBalance()
                .subtract(requestedAmount)
                .max(BigDecimal.ZERO)
                .setScale(2, RoundingMode.HALF_UP);
        return toPayoutResponse(saved, remaining);
    }

    @Override
    @Transactional
    public FinanceTransactionResponse createExpense(CreateExpenseRequest request) {
        Company company = makerCompanyResolver.requireActiveMakerCompany();
        BigDecimal scaledAmount = request.amount().setScale(2, RoundingMode.HALF_UP);

        MakerFinanceTransaction tx = new MakerFinanceTransaction();
        tx.setCompanyId(company.getId());
        tx.setTransactionDate(request.date());
        tx.setTransactionType(mapExpenseType(request.category()));
        tx.setTitle(request.title().trim());
        tx.setReference(trimToNull(request.reference()));
        tx.setCategory(request.category().name());
        tx.setAmount(scaledAmount.negate());
        tx.setTransactionStatus(FinanceTransactionStatus.COMPLETED);

        MakerFinanceTransaction saved = financeTransactionRepository.save(tx);
        return toTransactionResponse(saved);
    }

    @Override
    @Transactional
    public FinanceRatesResponse updateRates(UpdateFinanceRatesRequest request) {
        Company company = makerCompanyResolver.requireActiveMakerCompany();

        if (request.kwhRate() != null) {
            company.setKwhRate(request.kwhRate().setScale(4, RoundingMode.HALF_UP));
        }
        if (request.hourlyDepreciationRate() != null) {
            company.setHourlyDepreciationRate(request.hourlyDepreciationRate().setScale(2, RoundingMode.HALF_UP));
        }
        if (request.pixKeyType() != null) {
            company.setPixKeyType(request.pixKeyType());
        }
        if (request.pixKey() != null && !request.pixKey().isBlank()) {
            company.setPixKey(request.pixKey().trim());
        }

        if (company.getPixKey() != null && !company.getPixKey().isBlank() && company.getPixKeyType() == null) {
            throw new BadRequestException(MessageCd.VALIDATION_ERROR);
        }

        Company saved = companyRepository.save(company);
        if (request.kwhRate() != null || request.hourlyDepreciationRate() != null) {
            syncPersistedQuoteModuleRates(saved, request.kwhRate(), request.hourlyDepreciationRate());
            makerQuoteSettingsInMemoryCache.evictAfterCommit(saved.getId());
        }
        return new FinanceRatesResponse(
                resolveKwhRate(saved),
                resolveDepreciationRate(saved),
                MaskingUtil.maskPixKey(saved.getPixKeyType(), saved.getPixKey()),
                saved.getPixKeyType()
        );
    }

    private void syncPersistedQuoteModuleRates(
            Company company,
            BigDecimal kwhRate,
            BigDecimal hourlyDepreciationRate) {
        List<MakerQuoteSettings> existingRows = makerQuoteSettingsRepository
                .findByCompanyIdAndStatusOrderByExecutionOrderAscIdAsc(company.getId(), StatusCd.ACTIVE);
        if (existingRows == null || existingRows.isEmpty()) {
            return;
        }

        List<QuoteSettings> catalog = quoteSettingsInMemoryCache.getAllOrdered();
        if (catalog == null || catalog.isEmpty()) {
            catalog = quoteSettingsRepository.findByStatusOrderByPhaseOrderAscExecutionOrderAscIdAsc(StatusCd.ACTIVE);
        }
        if (catalog == null || catalog.isEmpty()) {
            return;
        }

        Long powerCatalogId = null;
        Long depreciationCatalogId = null;
        for (QuoteSettings module : catalog) {
            if (module == null || module.getCode() == null) {
                continue;
            }
            if ("POWER".equalsIgnoreCase(module.getCode().trim())) {
                powerCatalogId = module.getId();
            } else if ("DEPRECIATION".equalsIgnoreCase(module.getCode().trim())) {
                depreciationCatalogId = module.getId();
            }
        }

        List<MakerQuoteSettings> updatedRows = new ArrayList<>();
        for (MakerQuoteSettings row : existingRows) {
            if (kwhRate != null && Objects.equals(row.getQuoteSettingsId(), powerCatalogId)) {
                ObjectNode node = asMutableObject(row.getConfigJson());
                node.put("kwhRate", kwhRate.setScale(4, RoundingMode.HALF_UP));
                row.setConfigJson(jsonCodec.toJson(node));
                updatedRows.add(row);
            } else if (hourlyDepreciationRate != null && Objects.equals(row.getQuoteSettingsId(), depreciationCatalogId)) {
                ObjectNode node = asMutableObject(row.getConfigJson());
                node.put("hourlyDepreciationRate", hourlyDepreciationRate.setScale(2, RoundingMode.HALF_UP));
                row.setConfigJson(jsonCodec.toJson(node));
                updatedRows.add(row);
            }
        }

        if (!updatedRows.isEmpty()) {
            makerQuoteSettingsRepository.saveAll(updatedRows);
        }
    }

    private ObjectNode asMutableObject(String json) {
        JsonNode node = jsonCodec.readTree(json);
        if (node instanceof ObjectNode objNode) {
            return objNode;
        }
        return jsonCodec.createObjectNode();
    }

    public BalanceSnapshot computeBalanceSnapshot(Company company) {
        LedgerCalculation ledger = calculateLedger(company, YearMonth.now(ZoneOffset.UTC), false);
        return new BalanceSnapshot(ledger.availableBalance(), ledger.pendingBalance(), ledger.totalSales());
    }

    private LedgerCalculation calculateLedger(Company company, YearMonth currentMonth, boolean includeMonthlyCosts) {
        List<MakerFinanceTransaction> transactions = financeTransactionRepository
                .findAllByCompanyIdAndStatusOrderByTransactionDateDescCreateDtDesc(company.getId(), StatusCd.ACTIVE);
        List<Order> vendorOrders = orderRepository
                .findAllByVendorIdAndStatusOrderByCreateDtDesc(company.getId(), StatusCd.ACTIVE);

        BigDecimal settledSalesTotal = BigDecimal.ZERO;
        BigDecimal pendingTotal = BigDecimal.ZERO;
        BigDecimal payoutsTotal = BigDecimal.ZERO;
        BigDecimal platformFeesTotal = BigDecimal.ZERO;
        BigDecimal refundsTotal = BigDecimal.ZERO;
        long standaloneCompletedSales = 0L;

        Set<Long> recordedSaleOrderIds = new HashSet<>();
        Map<YearMonth, BigDecimal> revenueByMonth = new HashMap<>();
        Map<YearMonth, BigDecimal> expensesByMonth = new HashMap<>();
        Map<CostCategory, BigDecimal> currentMonthCosts = new EnumMap<>(CostCategory.class);
        Set<YearMonth> monthsWithExplicitFilament = new HashSet<>();
        Set<YearMonth> monthsWithExplicitEnergy = new HashSet<>();

        for (MakerFinanceTransaction tx : transactions) {
            if (tx == null || isInactiveFinancialStatus(tx.getTransactionStatus()) || tx.getTransactionType() == null) {
                continue;
            }
            BigDecimal amount = absMoney(tx.getAmount());
            YearMonth txMonth = tx.getTransactionDate() != null ? YearMonth.from(tx.getTransactionDate()) : currentMonth;

            switch (tx.getTransactionType()) {
                case SALE -> {
                    if (tx.getOrderId() != null) {
                        recordedSaleOrderIds.add(tx.getOrderId());
                    }
                    if (FinanceTransactionStatus.COMPLETED.equals(tx.getTransactionStatus())) {
                        settledSalesTotal = settledSalesTotal.add(amount);
                        revenueByMonth.merge(txMonth, amount, BigDecimal::add);
                        if (tx.getOrderId() == null) {
                            standaloneCompletedSales++;
                        }
                    } else if (isPendingOrProcessing(tx.getTransactionStatus())) {
                        pendingTotal = pendingTotal.add(amount);
                    }
                }
                case PAYOUT -> payoutsTotal = payoutsTotal.add(amount);
                case REFUND -> refundsTotal = refundsTotal.add(amount);
                case PLATFORM_FEE -> {
                    platformFeesTotal = platformFeesTotal.add(amount);
                    expensesByMonth.merge(txMonth, amount, BigDecimal::add);
                    if (txMonth.equals(currentMonth)) {
                        currentMonthCosts.merge(CostCategory.PLATFORM_FEES, amount, BigDecimal::add);
                    }
                }
                case FILAMENT -> {
                    monthsWithExplicitFilament.add(txMonth);
                    expensesByMonth.merge(txMonth, amount, BigDecimal::add);
                    if (txMonth.equals(currentMonth)) {
                        currentMonthCosts.merge(CostCategory.FILAMENTS_AND_RESINS, amount, BigDecimal::add);
                    }
                }
                case ENERGY -> {
                    monthsWithExplicitEnergy.add(txMonth);
                    expensesByMonth.merge(txMonth, amount, BigDecimal::add);
                    if (txMonth.equals(currentMonth)) {
                        currentMonthCosts.merge(CostCategory.ENERGY, amount, BigDecimal::add);
                    }
                }
                case MAINTENANCE -> {
                    expensesByMonth.merge(txMonth, amount, BigDecimal::add);
                    if (txMonth.equals(currentMonth)) {
                        currentMonthCosts.merge(CostCategory.MAINTENANCE, amount, BigDecimal::add);
                    }
                }
            }
        }

        long completedOrdersCount = 0L;
        for (Order order : vendorOrders) {
            if (order == null) {
                continue;
            }
            OrderStatus latestStatus = orderStatusService.findLatest(order);
            OrderStatusCd orderStatus = latestStatus.getStatusCd();
            BigDecimal orderAmount = nonNegative(order.getOrderTotal());
            boolean alreadyRecordedSale = order.getId() != null && recordedSaleOrderIds.contains(order.getId());

            if (OrderStatusCd.COMPLETED.equals(orderStatus)) {
                completedOrdersCount++;
                if (!alreadyRecordedSale) {
                    settledSalesTotal = settledSalesTotal.add(orderAmount);
                    YearMonth orderMonth = toYearMonth(
                            order.getUpdateDt() != null ? order.getUpdateDt() : order.getCreateDt(),
                            currentMonth
                    );
                    revenueByMonth.merge(orderMonth, orderAmount, BigDecimal::add);
                }
            } else if (PENDING_ORDER_STATUSES.contains(orderStatus) && !alreadyRecordedSale) {
                pendingTotal = pendingTotal.add(orderAmount);
            }
        }

        if (includeMonthlyCosts) {
            appendTelemetryCosts(
                    company,
                    currentMonth,
                    monthsWithExplicitFilament,
                    monthsWithExplicitEnergy,
                    expensesByMonth,
                    currentMonthCosts
            );
        }

        BigDecimal availableBalance = settledSalesTotal
                .subtract(payoutsTotal)
                .subtract(platformFeesTotal)
                .subtract(refundsTotal)
                .max(BigDecimal.ZERO)
                .setScale(2, RoundingMode.HALF_UP);
        BigDecimal pendingBalance = pendingTotal.setScale(2, RoundingMode.HALF_UP);

        return new LedgerCalculation(
                availableBalance,
                pendingBalance,
                completedOrdersCount + standaloneCompletedSales,
                revenueByMonth,
                expensesByMonth,
                currentMonthCosts
        );
    }

    private void appendTelemetryCosts(
            Company company,
            YearMonth currentMonth,
            Set<YearMonth> monthsWithExplicitFilament,
            Set<YearMonth> monthsWithExplicitEnergy,
            Map<YearMonth, BigDecimal> expensesByMonth,
            Map<CostCategory, BigDecimal> currentMonthCosts) {
        List<PrintQueueItem> queueJobs = printQueueItemRepository
                .findAllByCompanyIdAndStatusOrderByPositionAscCreateDtAsc(company.getId(), StatusCd.ACTIVE);
        if (queueJobs.isEmpty()) {
            return;
        }

        Map<Long, Asset> printersById = assetRepository.findAllByCompanyIdAndStatus(company.getId(), StatusCd.ACTIVE)
                .stream()
                .collect(Collectors.toMap(Asset::getId, a -> a, (a, b) -> a));
        Map<Long, Filament> filamentsById = filamentRepository.findAllByCompanyIdAndStatus(company.getId(), StatusCd.ACTIVE)
                .stream()
                .collect(Collectors.toMap(Filament::getId, f -> f, (a, b) -> a));

        BigDecimal kwhRate = resolveKwhRate(company);
        BigDecimal depRate = resolveDepreciationRate(company);
        YearMonth earliestSeriesMonth = currentMonth.minusMonths(MONTHLY_SERIES_MONTHS - 1L);

        Map<YearMonth, BigDecimal> depreciationByMonth = new HashMap<>();
        Map<YearMonth, BigDecimal> estimatedEnergyByMonth = new HashMap<>();
        Map<YearMonth, BigDecimal> estimatedFilamentByMonth = new HashMap<>();

        for (PrintQueueItem job : queueJobs) {
            if (job == null || !PrintJobStatus.COMPLETED.equals(job.getJobStatus())) {
                continue;
            }
            YearMonth jobMonth = toYearMonth(
                    job.getUpdateDt() != null ? job.getUpdateDt() : job.getCreateDt(),
                    currentMonth
            );
            if (jobMonth.isBefore(earliestSeriesMonth) || jobMonth.isAfter(currentMonth)) {
                continue;
            }

            int copiesFactor = resolveCompletedCopiesFactor(job);
            if (job.getEstimatedTimeMinutes() != null && job.getEstimatedTimeMinutes() > 0) {
                BigDecimal hours = BigDecimal.valueOf((long) job.getEstimatedTimeMinutes() * copiesFactor)
                        .divide(MINUTES_PER_HOUR, 6, RoundingMode.HALF_UP);
                depreciationByMonth.merge(jobMonth, hours.multiply(depRate), BigDecimal::add);

                Asset printer = job.getAssetId() != null ? printersById.get(job.getAssetId()) : null;
                int watts = printer != null && printer.getPowerWatts() != null && printer.getPowerWatts() > 0
                        ? printer.getPowerWatts()
                        : DEFAULT_POWER_WATTS;
                BigDecimal kwh = hours.multiply(BigDecimal.valueOf(watts))
                        .divide(WATTS_PER_KW, 6, RoundingMode.HALF_UP);
                estimatedEnergyByMonth.merge(jobMonth, kwh.multiply(kwhRate), BigDecimal::add);
            }

            if (job.getEstimatedWeightGrams() != null
                    && job.getEstimatedWeightGrams().compareTo(BigDecimal.ZERO) > 0
                    && job.getFilamentId() != null) {
                Filament filament = filamentsById.get(job.getFilamentId());
                if (filament != null && filament.getCostPerKg() != null
                        && filament.getCostPerKg().compareTo(BigDecimal.ZERO) > 0) {
                    BigDecimal grams = job.getEstimatedWeightGrams().multiply(BigDecimal.valueOf(copiesFactor));
                    BigDecimal cost = grams.multiply(filament.getCostPerKg())
                            .divide(GRAMS_PER_KG, 6, RoundingMode.HALF_UP);
                    estimatedFilamentByMonth.merge(jobMonth, cost, BigDecimal::add);
                }
            }
        }

        depreciationByMonth.forEach((month, rawAmount) -> {
            BigDecimal scaled = rawAmount.setScale(2, RoundingMode.HALF_UP);
            if (scaled.compareTo(BigDecimal.ZERO) > 0) {
                expensesByMonth.merge(month, scaled, BigDecimal::add);
                if (month.equals(currentMonth)) {
                    currentMonthCosts.merge(CostCategory.FLEET_DEPRECIATION, scaled, BigDecimal::add);
                }
            }
        });

        estimatedEnergyByMonth.forEach((month, rawAmount) -> {
            if (!monthsWithExplicitEnergy.contains(month)) {
                BigDecimal scaled = rawAmount.setScale(2, RoundingMode.HALF_UP);
                if (scaled.compareTo(BigDecimal.ZERO) > 0) {
                    expensesByMonth.merge(month, scaled, BigDecimal::add);
                    if (month.equals(currentMonth)) {
                        currentMonthCosts.merge(CostCategory.ENERGY, scaled, BigDecimal::add);
                    }
                }
            }
        });

        estimatedFilamentByMonth.forEach((month, rawAmount) -> {
            if (!monthsWithExplicitFilament.contains(month)) {
                BigDecimal scaled = rawAmount.setScale(2, RoundingMode.HALF_UP);
                if (scaled.compareTo(BigDecimal.ZERO) > 0) {
                    expensesByMonth.merge(month, scaled, BigDecimal::add);
                    if (month.equals(currentMonth)) {
                        currentMonthCosts.merge(CostCategory.FILAMENTS_AND_RESINS, scaled, BigDecimal::add);
                    }
                }
            }
        });
    }

    private PayoutResponse resolveIdempotentPayout(
            MakerFinanceTransaction existing,
            Company company,
            BigDecimal requestedAmount,
            PixKeyType requestedPixKeyType,
            String requestedMaskedPix) {
        if (!Objects.equals(existing.getCompanyId(), company.getId())
                || !FinanceTransactionType.PAYOUT.equals(existing.getTransactionType())) {
            throw new ConflictException(MessageCd.CONFLICT);
        }
        BigDecimal existingMagnitude = absMoney(existing.getAmount());
        boolean samePayload = existingMagnitude.compareTo(requestedAmount) == 0
                && Objects.equals(existing.getPixKeyType(), requestedPixKeyType)
                && Objects.equals(existing.getPixKeyMasked(), requestedMaskedPix);
        if (!samePayload) {
            throw new ConflictException(MessageCd.CONFLICT);
        }
        BalanceSnapshot currentBalance = computeBalanceSnapshot(company);
        return toPayoutResponse(existing, currentBalance.availableBalance());
    }

    private List<CostBreakdownDto> buildCostBreakdown(Map<CostCategory, BigDecimal> currentMonthCosts) {
        BigDecimal total = currentMonthCosts.values().stream()
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
        if (total.compareTo(BigDecimal.ZERO) <= 0) {
            return List.of();
        }
        return currentMonthCosts.entrySet().stream()
                .filter(entry -> entry.getValue() != null && entry.getValue().compareTo(BigDecimal.ZERO) > 0)
                .sorted(Map.Entry.<CostCategory, BigDecimal>comparingByValue().reversed())
                .map(entry -> {
                    BigDecimal amount = entry.getValue().setScale(2, RoundingMode.HALF_UP);
                    BigDecimal percentage = amount.multiply(ONE_HUNDRED).divide(total, 2, RoundingMode.HALF_UP);
                    return new CostBreakdownDto(entry.getKey(), amount, percentage);
                })
                .toList();
    }

    private List<MonthlyFinanceSeriesDto> buildMonthlySeries(
            YearMonth currentMonth,
            Map<YearMonth, BigDecimal> revenueByMonth,
            Map<YearMonth, BigDecimal> expensesByMonth) {
        List<MonthlyFinanceSeriesDto> series = new ArrayList<>(MONTHLY_SERIES_MONTHS);
        for (int offset = MONTHLY_SERIES_MONTHS - 1; offset >= 0; offset--) {
            YearMonth month = currentMonth.minusMonths(offset);
            BigDecimal revenue = revenueByMonth.getOrDefault(month, BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
            BigDecimal expenses = expensesByMonth.getOrDefault(month, BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
            series.add(new MonthlyFinanceSeriesDto(month, revenue, expenses));
        }
        return series;
    }

    private Comparator<MakerFinanceTransaction> buildTransactionComparator(Pageable pageable) {
        Comparator<MakerFinanceTransaction> defaultOrder = Comparator
                .comparing(MakerFinanceTransaction::getTransactionDate, Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(MakerFinanceTransaction::getCreateDt, Comparator.nullsLast(Comparator.reverseOrder()));

        if (pageable == null || pageable.getSort().isUnsorted()) {
            return defaultOrder;
        }

        Comparator<MakerFinanceTransaction> custom = null;
        for (Sort.Order sortOrder : pageable.getSort()) {
            Comparator<MakerFinanceTransaction> fieldComparator = switch (sortOrder.getProperty()) {
                case "transactionDate" -> Comparator.comparing(
                        MakerFinanceTransaction::getTransactionDate, Comparator.nullsLast(Comparator.naturalOrder())
                );
                case "amount" -> Comparator.comparing(
                        this::displaySignedAmount, Comparator.nullsLast(Comparator.naturalOrder())
                );
                case "type", "transactionType" -> Comparator.comparing(
                        tx -> tx.getTransactionType() != null ? tx.getTransactionType().name() : "",
                        String::compareTo
                );
                case "status", "transactionStatus" -> Comparator.comparing(
                        tx -> tx.getTransactionStatus() != null ? tx.getTransactionStatus().name() : "",
                        String::compareTo
                );
                case "createdAt", "createDt" -> Comparator.comparing(
                        MakerFinanceTransaction::getCreateDt, Comparator.nullsLast(Comparator.naturalOrder())
                );
                default -> null;
            };
            if (fieldComparator != null) {
                if (sortOrder.isDescending()) {
                    fieldComparator = fieldComparator.reversed();
                }
                custom = custom == null ? fieldComparator : custom.thenComparing(fieldComparator);
            }
        }
        return custom != null ? custom.thenComparing(defaultOrder) : defaultOrder;
    }

    private FinanceTransactionResponse toTransactionResponse(MakerFinanceTransaction tx) {
        return new FinanceTransactionResponse(
                tx.getGuid(),
                tx.getTransactionDate(),
                tx.getTransactionType(),
                tx.getTitle(),
                tx.getReference(),
                tx.getCategory(),
                displaySignedAmount(tx),
                tx.getTransactionStatus(),
                tx.getCreateDt()
        );
    }

    private PayoutResponse toPayoutResponse(MakerFinanceTransaction tx, BigDecimal remainingAvailableBalance) {
        return new PayoutResponse(
                tx.getGuid(),
                tx.getGuid(),
                absMoney(tx.getAmount()).setScale(2, RoundingMode.HALF_UP),
                tx.getPixKeyType(),
                tx.getPixKeyMasked(),
                tx.getTransactionStatus(),
                remainingAvailableBalance,
                tx.getCreateDt() != null ? tx.getCreateDt() : Instant.now()
        );
    }

    private BigDecimal displaySignedAmount(MakerFinanceTransaction tx) {
        BigDecimal magnitude = absMoney(tx.getAmount()).setScale(2, RoundingMode.HALF_UP);
        return FinanceTransactionType.SALE.equals(tx.getTransactionType())
                ? magnitude
                : magnitude.negate();
    }

    private FinanceTransactionType mapExpenseType(ExpenseCategory category) {
        return switch (category) {
            case FILAMENT -> FinanceTransactionType.FILAMENT;
            case ENERGY -> FinanceTransactionType.ENERGY;
            case MAINTENANCE -> FinanceTransactionType.MAINTENANCE;
        };
    }

    private BigDecimal resolveKwhRate(Company company) {
        return company.getKwhRate() != null
                ? company.getKwhRate()
                : DEFAULT_KWH_RATE;
    }

    private BigDecimal resolveDepreciationRate(Company company) {
        return company.getHourlyDepreciationRate() != null
                ? company.getHourlyDepreciationRate()
                : DEFAULT_HOURLY_DEPRECIATION_RATE;
    }

    private boolean isInactiveFinancialStatus(FinanceTransactionStatus status) {
        return FinanceTransactionStatus.FAILED.equals(status)
                || FinanceTransactionStatus.CANCELLED.equals(status);
    }

    private boolean isPendingOrProcessing(FinanceTransactionStatus status) {
        return FinanceTransactionStatus.PENDING.equals(status)
                || FinanceTransactionStatus.PROCESSING.equals(status);
    }

    private YearMonth toYearMonth(Instant instant, YearMonth fallback) {
        return instant != null ? YearMonth.from(instant.atZone(ZoneOffset.UTC)) : fallback;
    }

    private int resolveCompletedCopiesFactor(PrintQueueItem job) {
        if (job.getCompletedCopies() != null && job.getCompletedCopies() > 0) {
            return job.getCompletedCopies();
        }
        if (job.getCopies() != null && job.getCopies() > 0) {
            return job.getCopies();
        }
        return 1;
    }

    private BigDecimal absMoney(BigDecimal value) {
        return value != null ? value.abs() : BigDecimal.ZERO;
    }

    private BigDecimal nonNegative(BigDecimal value) {
        return value != null ? value.max(BigDecimal.ZERO) : BigDecimal.ZERO;
    }

    private String trimToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    public record BalanceSnapshot(
            BigDecimal availableBalance,
            BigDecimal pendingBalance,
            long totalSales
    ) {
    }

    private record LedgerCalculation(
            BigDecimal availableBalance,
            BigDecimal pendingBalance,
            long totalSales,
            Map<YearMonth, BigDecimal> revenueByMonth,
            Map<YearMonth, BigDecimal> expensesByMonth,
            Map<CostCategory, BigDecimal> currentMonthCostByCategory
    ) {
    }
}
