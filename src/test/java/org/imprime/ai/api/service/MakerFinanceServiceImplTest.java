package org.imprime.ai.api.service;

import org.imprime.ai.api.http.request.maker.CreateExpenseRequest;
import org.imprime.ai.api.http.request.maker.CreatePayoutRequest;
import org.imprime.ai.api.http.request.maker.UpdateFinanceRatesRequest;
import org.imprime.ai.api.http.response.PageResponse;
import org.imprime.ai.api.http.response.maker.FinanceRatesResponse;
import org.imprime.ai.api.http.response.maker.FinanceTransactionResponse;
import org.imprime.ai.api.http.response.maker.MakerFinanceSummaryResponse;
import org.imprime.ai.api.http.response.maker.PayoutResponse;
import org.imprime.ai.api.model.Company;
import org.imprime.ai.api.model.Filament;
import org.imprime.ai.api.model.MakerFinanceTransaction;
import org.imprime.ai.api.model.Order;
import org.imprime.ai.api.model.OrderStatus;
import org.imprime.ai.api.model.PrintQueueItem;
import org.imprime.ai.api.model.enums.CostCategory;
import org.imprime.ai.api.model.enums.ExpenseCategory;
import org.imprime.ai.api.model.enums.FinanceTransactionStatus;
import org.imprime.ai.api.model.enums.FinanceTransactionType;
import org.imprime.ai.api.model.enums.OrderStatusCd;
import org.imprime.ai.api.model.enums.PixKeyType;
import org.imprime.ai.api.model.enums.PrintJobStatus;
import org.imprime.ai.api.model.enums.StatusCd;
import org.imprime.ai.api.model.exception.BadRequestException;
import org.imprime.ai.api.model.exception.ConflictException;
import org.imprime.ai.api.repo.db.AssetRepository;
import org.imprime.ai.api.repo.db.CompanyRepository;
import org.imprime.ai.api.repo.db.FilamentRepository;
import org.imprime.ai.api.repo.db.MakerFinanceTransactionRepository;
import org.imprime.ai.api.repo.db.OrderRepository;
import org.imprime.ai.api.repo.db.PrintQueueItemRepository;
import org.imprime.ai.api.service.impl.MakerFinanceServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MakerFinanceServiceImplTest {

    @Mock
    private MakerCompanyResolver makerCompanyResolver;
    @Mock
    private MakerFinanceTransactionRepository financeTransactionRepository;
    @Mock
    private OrderRepository orderRepository;
    @Mock
    private OrderStatusService orderStatusService;
    @Mock
    private CompanyRepository companyRepository;
    @Mock
    private AssetRepository assetRepository;
    @Mock
    private PrintQueueItemRepository printQueueItemRepository;
    @Mock
    private FilamentRepository filamentRepository;

    @InjectMocks
    private MakerFinanceServiceImpl service;

    private Company company;

    @BeforeEach
    void setUp() {
        company = new Company();
        company.setId(10L);
        company.setGuid("comp-10");
        company.setPixKeyType(PixKeyType.EMAIL);
        company.setPixKey("financeiro@studio3d.com.br");
        company.setKwhRate(new BigDecimal("0.9000"));
        company.setHourlyDepreciationRate(new BigDecimal("2.00"));
        company.setStatus(StatusCd.ACTIVE);
    }

    @Test
    void getSummaryReconcilesLedgerOrdersCostBreakdownAndMonthlySeries() {
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        Instant nowInstant = Instant.now();

        Order completedRecordedOrder = new Order();
        completedRecordedOrder.setId(101L);
        completedRecordedOrder.setOrderTotal(new BigDecimal("400.00"));
        completedRecordedOrder.setUpdateDt(nowInstant);

        Order completedUnrecordedOrder = new Order();
        completedUnrecordedOrder.setId(102L);
        completedUnrecordedOrder.setOrderTotal(new BigDecimal("300.00"));
        completedUnrecordedOrder.setUpdateDt(nowInstant);

        Order inProductionOrder = new Order();
        inProductionOrder.setId(103L);
        inProductionOrder.setOrderTotal(new BigDecimal("250.00"));
        inProductionOrder.setUpdateDt(nowInstant);

        MakerFinanceTransaction recordedSale = tx(
                "tx-sale", 101L, today, FinanceTransactionType.SALE,
                new BigDecimal("400.00"), FinanceTransactionStatus.COMPLETED, null
        );
        MakerFinanceTransaction feeTx = tx(
                "tx-fee", 101L, today, FinanceTransactionType.PLATFORM_FEE,
                new BigDecimal("-40.00"), FinanceTransactionStatus.COMPLETED, null
        );
        MakerFinanceTransaction payoutTx = tx(
                "tx-payout", null, today, FinanceTransactionType.PAYOUT,
                new BigDecimal("-100.00"), FinanceTransactionStatus.COMPLETED, null
        );
        MakerFinanceTransaction filamentTx = tx(
                "tx-fil", null, today, FinanceTransactionType.FILAMENT,
                new BigDecimal("-120.00"), FinanceTransactionStatus.COMPLETED, "FILAMENT"
        );

        Filament filament = new Filament();
        filament.setId(30L);
        filament.setCostPerKg(new BigDecimal("100.00"));

        PrintQueueItem completedJob = new PrintQueueItem();
        completedJob.setId(501L);
        completedJob.setJobStatus(PrintJobStatus.COMPLETED);
        completedJob.setEstimatedTimeMinutes(120);
        completedJob.setEstimatedWeightGrams(new BigDecimal("50.00"));
        completedJob.setFilamentId(30L);
        completedJob.setCopies(1);
        completedJob.setCompletedCopies(1);
        completedJob.setUpdateDt(nowInstant);

        when(makerCompanyResolver.requireActiveMakerCompany()).thenReturn(company);
        when(financeTransactionRepository.findAllByCompanyIdAndStatusOrderByTransactionDateDescCreateDtDesc(10L, StatusCd.ACTIVE))
                .thenReturn(List.of(recordedSale, feeTx, payoutTx, filamentTx));
        when(orderRepository.findAllByVendorIdAndStatusOrderByCreateDtDesc(10L, StatusCd.ACTIVE))
                .thenReturn(List.of(completedRecordedOrder, completedUnrecordedOrder, inProductionOrder));
        when(orderStatusService.findLatest(completedRecordedOrder)).thenReturn(statusOf(OrderStatusCd.COMPLETED));
        when(orderStatusService.findLatest(completedUnrecordedOrder)).thenReturn(statusOf(OrderStatusCd.COMPLETED));
        when(orderStatusService.findLatest(inProductionOrder)).thenReturn(statusOf(OrderStatusCd.IN_PRODUCTION));
        when(printQueueItemRepository.findAllByCompanyIdAndStatusOrderByPositionAscCreateDtAsc(10L, StatusCd.ACTIVE))
                .thenReturn(List.of(completedJob));
        when(assetRepository.findAllByCompanyIdAndStatus(10L, StatusCd.ACTIVE)).thenReturn(List.of());
        when(filamentRepository.findAllByCompanyIdAndStatus(10L, StatusCd.ACTIVE)).thenReturn(List.of(filament));

        MakerFinanceSummaryResponse summary = service.getSummary();

        // Settled sales = 400 + 300 (no double-count of order 101!) = 700; minus fee 40 and payout 100 = 560.00
        assertEquals(new BigDecimal("560.00"), summary.availableBalance());
        assertEquals(new BigDecimal("250.00"), summary.pendingBalance());
        assertEquals(new BigDecimal("700.00"), summary.monthlyRevenue());
        // Expenses = 40 (fee) + 120 (explicit filament, skipped telemetry fallback) + 4.00 (2h * 2.00 dep) + 0.54 (2h * 0.3kW * 0.90)
        assertEquals(new BigDecimal("164.54"), summary.monthlyExpenses());
        assertEquals(new BigDecimal("535.46"), summary.netProfit());
        assertEquals("fin***@studio3d.com.br", summary.pixKeyMasked());
        assertEquals(6, summary.monthlySeries().size());
        assertEquals(YearMonth.from(today), summary.monthlySeries().getLast().month());
        assertEquals(new BigDecimal("700.00"), summary.monthlySeries().getLast().revenue());
        assertEquals(new BigDecimal("164.54"), summary.monthlySeries().getLast().expenses());
        assertTrue(summary.costBreakdown().stream().anyMatch(c -> c.category() == CostCategory.FILAMENTS_AND_RESINS));
        assertTrue(summary.costBreakdown().stream().anyMatch(c -> c.category() == CostCategory.FLEET_DEPRECIATION));
    }

    @Test
    void requestPayoutEnforcesIdempotencyPayloadMatchAndAvailableBalance() {
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        MakerFinanceTransaction existingSale = tx(
                "tx-sale-1", null, today, FinanceTransactionType.SALE,
                new BigDecimal("600.00"), FinanceTransactionStatus.COMPLETED, null
        );
        when(makerCompanyResolver.requireActiveMakerCompany()).thenReturn(company);
        when(orderRepository.findAllByVendorIdAndStatusOrderByCreateDtDesc(10L, StatusCd.ACTIVE))
                .thenReturn(List.of());
        when(financeTransactionRepository.findAllByCompanyIdAndStatusOrderByTransactionDateDescCreateDtDesc(10L, StatusCd.ACTIVE))
                .thenReturn(List.of(existingSale));
        when(financeTransactionRepository.findByIdempotencyKeyAndStatus("idem-123", StatusCd.ACTIVE))
                .thenReturn(Optional.empty());
        when(financeTransactionRepository.save(any(MakerFinanceTransaction.class))).thenAnswer(inv -> {
            MakerFinanceTransaction saved = inv.getArgument(0);
            saved.setGuid("payout-guid-1");
            return saved;
        });

        CreatePayoutRequest payoutReq = new CreatePayoutRequest(
                new BigDecimal("250.00"),
                "financeiro@studio3d.com.br",
                PixKeyType.EMAIL
        );

        PayoutResponse first = service.requestPayout("idem-123", payoutReq);
        assertEquals("payout-guid-1", first.payoutGuid());
        assertEquals(new BigDecimal("250.00"), first.amount());
        assertEquals(FinanceTransactionStatus.PROCESSING, first.status());
        assertEquals("fin***@studio3d.com.br", first.pixKeyMasked());
        assertEquals(new BigDecimal("350.00"), first.remainingAvailableBalance());

        // Retry with same idempotency key and matching payload must NOT save again
        MakerFinanceTransaction persistedPayout = tx(
                "payout-guid-1", null, today, FinanceTransactionType.PAYOUT,
                new BigDecimal("-250.00"), FinanceTransactionStatus.PROCESSING, "PIX"
        );
        persistedPayout.setPixKeyType(PixKeyType.EMAIL);
        persistedPayout.setPixKeyMasked("fin***@studio3d.com.br");
        persistedPayout.setIdempotencyKey("idem-123");

        when(financeTransactionRepository.findByIdempotencyKeyAndStatus("idem-123", StatusCd.ACTIVE))
                .thenReturn(Optional.of(persistedPayout));
        when(financeTransactionRepository.findAllByCompanyIdAndStatusOrderByTransactionDateDescCreateDtDesc(10L, StatusCd.ACTIVE))
                .thenReturn(List.of(existingSale, persistedPayout));

        PayoutResponse replay = service.requestPayout("idem-123", payoutReq);
        assertEquals("payout-guid-1", replay.payoutGuid());
        assertEquals(new BigDecimal("350.00"), replay.remainingAvailableBalance());
        verify(financeTransactionRepository, times(1)).save(any());

        // Same key with mismatched amount must fail with 409 Conflict
        CreatePayoutRequest mismatchedReq = new CreatePayoutRequest(
                new BigDecimal("260.00"),
                "financeiro@studio3d.com.br",
                PixKeyType.EMAIL
        );
        assertThrows(ConflictException.class, () -> service.requestPayout("idem-123", mismatchedReq));

        // New key exceeding available balance (350.00) must fail with 400 Insufficient Balance
        when(financeTransactionRepository.findByIdempotencyKeyAndStatus("idem-over", StatusCd.ACTIVE))
                .thenReturn(Optional.empty());
        CreatePayoutRequest overBalanceReq = new CreatePayoutRequest(
                new BigDecimal("400.00"),
                "financeiro@studio3d.com.br",
                PixKeyType.EMAIL
        );
        assertThrows(BadRequestException.class, () -> service.requestPayout("idem-over", overBalanceReq));
    }

    @Test
    void createExpenseSavesNegativeAmountWithCategory() {
        when(makerCompanyResolver.requireActiveMakerCompany()).thenReturn(company);
        when(financeTransactionRepository.save(any(MakerFinanceTransaction.class))).thenAnswer(inv -> {
            MakerFinanceTransaction saved = inv.getArgument(0);
            saved.setGuid("exp-1");
            return saved;
        });

        LocalDate expenseDate = LocalDate.of(2026, 11, 10);
        FinanceTransactionResponse response = service.createExpense(new CreateExpenseRequest(
                "2x Rolos PETG Preto",
                ExpenseCategory.FILAMENT,
                new BigDecimal("240.00"),
                expenseDate,
                "NF-90112"
        ));

        ArgumentCaptor<MakerFinanceTransaction> captor = ArgumentCaptor.forClass(MakerFinanceTransaction.class);
        verify(financeTransactionRepository).save(captor.capture());

        assertEquals(new BigDecimal("-240.00"), captor.getValue().getAmount());
        assertEquals(FinanceTransactionType.FILAMENT, captor.getValue().getTransactionType());
        assertEquals("FILAMENT", response.category());
        assertEquals("NF-90112", response.reference());
        assertEquals(new BigDecimal("-240.00"), response.amount());
    }

    @Test
    void updateRatesUpdatesCompanyRatesAndMasksPix() {
        when(makerCompanyResolver.requireActiveMakerCompany()).thenReturn(company);
        when(companyRepository.save(any(Company.class))).thenAnswer(inv -> inv.getArgument(0));

        FinanceRatesResponse updated = service.updateRates(new UpdateFinanceRatesRequest(
                new BigDecimal("0.9850"),
                new BigDecimal("3.25"),
                "+5511999998888",
                PixKeyType.PHONE
        ));

        assertEquals(new BigDecimal("0.9850"), updated.kwhRate());
        assertEquals(new BigDecimal("3.25"), updated.hourlyDepreciationRate());
        assertEquals(PixKeyType.PHONE, updated.pixKeyType());
        assertEquals("+55***88", updated.pixKeyMasked());
    }

    @Test
    void listTransactionsFiltersByTypeStatusAndDatesAndRejectsInvertedRange() {
        assertThrows(BadRequestException.class, () -> service.listTransactions(
                null, null, LocalDate.of(2026, 11, 20), LocalDate.of(2026, 11, 1), PageRequest.of(0, 10)
        ));

        when(makerCompanyResolver.requireActiveMakerCompany()).thenReturn(company);
        MakerFinanceTransaction t1 = tx(
                "t1", null, LocalDate.of(2026, 11, 15), FinanceTransactionType.SALE,
                new BigDecimal("150.00"), FinanceTransactionStatus.COMPLETED, null
        );
        MakerFinanceTransaction t2 = tx(
                "t2", null, LocalDate.of(2026, 11, 16), FinanceTransactionType.ENERGY,
                new BigDecimal("-45.00"), FinanceTransactionStatus.COMPLETED, "ENERGY"
        );
        MakerFinanceTransaction t3 = tx(
                "t3", null, LocalDate.of(2026, 11, 18), FinanceTransactionType.PAYOUT,
                new BigDecimal("-90.00"), FinanceTransactionStatus.PROCESSING, "PIX"
        );

        when(financeTransactionRepository.findAllByCompanyIdAndStatusOrderByTransactionDateDescCreateDtDesc(10L, StatusCd.ACTIVE))
                .thenReturn(List.of(t3, t2, t1));

        PageResponse<FinanceTransactionResponse> filtered = service.listTransactions(
                FinanceTransactionType.ENERGY,
                FinanceTransactionStatus.COMPLETED,
                LocalDate.of(2026, 11, 10),
                LocalDate.of(2026, 11, 17),
                PageRequest.of(0, 10)
        );

        assertEquals(1, filtered.content().size());
        assertEquals("t2", filtered.content().getFirst().guid());
        assertEquals(new BigDecimal("-45.00"), filtered.content().getFirst().amount());
    }

    private MakerFinanceTransaction tx(
            String guid,
            Long orderId,
            LocalDate date,
            FinanceTransactionType type,
            BigDecimal amount,
            FinanceTransactionStatus status,
            String category) {
        MakerFinanceTransaction tx = new MakerFinanceTransaction();
        tx.setCompanyId(10L);
        tx.setGuid(guid);
        tx.setOrderId(orderId);
        tx.setTransactionDate(date);
        tx.setTransactionType(type);
        tx.setTitle("Tx " + guid);
        tx.setCategory(category);
        tx.setAmount(amount);
        tx.setTransactionStatus(status);
        tx.setStatus(StatusCd.ACTIVE);
        tx.setCreateDt(date.atStartOfDay().toInstant(ZoneOffset.UTC));
        return tx;
    }

    private OrderStatus statusOf(OrderStatusCd statusCd) {
        OrderStatus st = new OrderStatus();
        st.setStatusCd(statusCd);
        return st;
    }
}
