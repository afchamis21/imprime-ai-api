package org.imprime.ai.api.service;

import org.imprime.ai.api.http.response.maker.MakerStatsResponse;
import org.imprime.ai.api.model.Asset;
import org.imprime.ai.api.model.Company;
import org.imprime.ai.api.model.Filament;
import org.imprime.ai.api.model.PrintQueueItem;
import org.imprime.ai.api.model.enums.MaterialType;
import org.imprime.ai.api.model.enums.PrintJobStatus;
import org.imprime.ai.api.model.enums.PrinterStatus;
import org.imprime.ai.api.model.enums.StatsRange;
import org.imprime.ai.api.model.enums.StatusCd;
import org.imprime.ai.api.model.exception.BadRequestException;
import org.imprime.ai.api.repo.db.AssetRepository;
import org.imprime.ai.api.repo.db.FilamentRepository;
import org.imprime.ai.api.repo.db.PrintQueueItemRepository;
import org.imprime.ai.api.service.impl.MakerAnalyticsServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MakerAnalyticsServiceImplTest {

    @Mock
    private MakerCompanyResolver makerCompanyResolver;
    @Mock
    private PrintQueueItemRepository printQueueItemRepository;
    @Mock
    private AssetRepository assetRepository;
    @Mock
    private FilamentRepository filamentRepository;

    @InjectMocks
    private MakerAnalyticsServiceImpl service;

    private Company company;
    private Asset printerA;
    private Filament plaSpool;

    @BeforeEach
    void setUp() {
        company = new Company();
        company.setId(10L);
        company.setGuid("comp-10");
        company.setKwhRate(new BigDecimal("1.0000"));
        company.setStatus(StatusCd.ACTIVE);

        printerA = new Asset();
        printerA.setId(1L);
        printerA.setGuid("prt-1");
        printerA.setName("Bambu X1C");
        printerA.setModel("Bambu Lab X1C");
        printerA.setPowerWatts(500);
        printerA.setPrinterStatus(PrinterStatus.ONLINE);
        printerA.setStatus(StatusCd.ACTIVE);

        plaSpool = new Filament();
        plaSpool.setId(100L);
        plaSpool.setMaterial(MaterialType.PLA);
        plaSpool.setColorHex("#22c55e");
        plaSpool.setCostPerKg(new BigDecimal("120.00"));
        plaSpool.setStatus(StatusCd.ACTIVE);
    }

    @Test
    void getStatsAggregatesPrintHoursSuccessRateMaterialEnergyAndTrends() {
        Instant todayInstant = LocalDate.now(ZoneOffset.UTC).atStartOfDay().plusHours(10).toInstant(ZoneOffset.UTC);
        Instant twoDaysAgo = todayInstant.minus(2, ChronoUnit.DAYS);

        PrintQueueItem completedJob1 = new PrintQueueItem();
        completedJob1.setId(1L);
        completedJob1.setAssetId(1L);
        completedJob1.setFilamentId(100L);
        completedJob1.setMaterial(MaterialType.PLA);
        completedJob1.setColorHex("#22c55e");
        completedJob1.setEstimatedTimeMinutes(120);
        completedJob1.setEstimatedWeightGrams(new BigDecimal("200.00"));
        completedJob1.setCopies(1);
        completedJob1.setCompletedCopies(1);
        completedJob1.setJobStatus(PrintJobStatus.COMPLETED);
        completedJob1.setUpdateDt(todayInstant);

        PrintQueueItem completedJob2 = new PrintQueueItem();
        completedJob2.setId(2L);
        completedJob2.setAssetId(1L);
        completedJob2.setMaterial(MaterialType.PETG);
        completedJob2.setEstimatedTimeMinutes(60);
        completedJob2.setEstimatedWeightGrams(new BigDecimal("100.00"));
        completedJob2.setCopies(2);
        completedJob2.setCompletedCopies(2);
        completedJob2.setJobStatus(PrintJobStatus.COMPLETED);
        completedJob2.setUpdateDt(twoDaysAgo);

        PrintQueueItem failedJob = new PrintQueueItem();
        failedJob.setId(3L);
        failedJob.setAssetId(1L);
        failedJob.setMaterial(MaterialType.PLA);
        failedJob.setEstimatedTimeMinutes(60);
        failedJob.setProgressPercent(50);
        failedJob.setEstimatedWeightGrams(new BigDecimal("50.00"));
        failedJob.setJobStatus(PrintJobStatus.FAILED);
        failedJob.setUpdateDt(twoDaysAgo);

        PrintQueueItem cancelledJob = new PrintQueueItem();
        cancelledJob.setId(4L);
        cancelledJob.setAssetId(1L);
        cancelledJob.setJobStatus(PrintJobStatus.CANCELLED);
        cancelledJob.setUpdateDt(todayInstant);

        // Active queued job should be excluded from historical terminal KPIs
        PrintQueueItem activeJob = new PrintQueueItem();
        activeJob.setId(5L);
        activeJob.setAssetId(1L);
        activeJob.setJobStatus(PrintJobStatus.QUEUED);
        activeJob.setCreateDt(todayInstant);

        when(makerCompanyResolver.requireActiveMakerCompany()).thenReturn(company);
        when(assetRepository.findAllByCompanyIdAndStatus(10L, StatusCd.ACTIVE)).thenReturn(List.of(printerA));
        when(filamentRepository.findAllByCompanyIdAndStatus(10L, StatusCd.ACTIVE)).thenReturn(List.of(plaSpool));
        when(printQueueItemRepository.findAllByCompanyIdAndStatusOrderByPositionAscCreateDtAsc(10L, StatusCd.ACTIVE))
                .thenReturn(List.of(completedJob1, completedJob2, failedJob, cancelledJob, activeJob));

        MakerStatsResponse stats = service.getStats(StatsRange.LAST_7_DAYS, null, null);

        // Total terminal prints = 2 completed + 1 failed + 1 cancelled = 4 -> 50.00% success
        assertEquals(StatsRange.LAST_7_DAYS, stats.range());
        assertEquals(4, stats.totalPrints());
        assertEquals(2, stats.successfulPrints());
        assertEquals(1, stats.failedPrints());
        assertEquals(1, stats.cancelledPrints());
        assertEquals(new BigDecimal("50.00"), stats.successRatePercent());

        // Hours: 120m (2h) + 60m*2 (2h) + 60m*50% (0.5h) = 4.50h; 500W * 4.5h = 2.25 kWh * R$ 1.00 = R$ 2.25
        assertEquals(new BigDecimal("4.50"), stats.totalPrintHours());
        assertEquals(new BigDecimal("2.25"), stats.totalEnergyKwh());
        assertEquals(new BigDecimal("2.25"), stats.estimatedEnergyCost());
        assertEquals(new BigDecimal("2.25"), stats.avgPrintDurationHours());

        // Filament: 200 (PLA) + 50 (PLA failed at 50%) + 100*2 (PETG) = 450.00g
        assertEquals(new BigDecimal("450.00"), stats.totalFilamentGrams());
        assertEquals(2, stats.materialBreakdown().size());
        assertEquals(7, stats.dailyTrend().size());
        assertEquals(7, stats.heatmap().size());
        assertEquals(1, stats.printerUtilization().size());
        assertEquals(new BigDecimal("4.50"), stats.printerUtilization().getFirst().hours());
        assertEquals(3, stats.printerUtilization().getFirst().printsCount());
        assertTrue(stats.heatmap().stream().anyMatch(point -> point.intensityLevel() == 4));
    }

    @Test
    void getStatsRejectsInvertedCustomDatesAndRespectsCustomRangeWindow() {
        assertThrows(BadRequestException.class, () -> service.getStats(
                StatsRange.LAST_30_DAYS,
                LocalDate.of(2026, 11, 15),
                LocalDate.of(2026, 11, 1)
        ));

        when(makerCompanyResolver.requireActiveMakerCompany()).thenReturn(company);
        when(assetRepository.findAllByCompanyIdAndStatus(10L, StatusCd.ACTIVE)).thenReturn(List.of());
        when(filamentRepository.findAllByCompanyIdAndStatus(10L, StatusCd.ACTIVE)).thenReturn(List.of());

        PrintQueueItem insideJob = new PrintQueueItem();
        insideJob.setId(10L);
        insideJob.setJobStatus(PrintJobStatus.COMPLETED);
        insideJob.setEstimatedTimeMinutes(60);
        insideJob.setCopies(1);
        insideJob.setCompletedCopies(1);
        insideJob.setUpdateDt(LocalDate.of(2026, 10, 12).atStartOfDay().toInstant(ZoneOffset.UTC));

        PrintQueueItem outsideJob = new PrintQueueItem();
        outsideJob.setId(11L);
        outsideJob.setJobStatus(PrintJobStatus.COMPLETED);
        outsideJob.setEstimatedTimeMinutes(180);
        outsideJob.setCopies(1);
        outsideJob.setCompletedCopies(1);
        outsideJob.setUpdateDt(LocalDate.of(2026, 10, 20).atStartOfDay().toInstant(ZoneOffset.UTC));

        when(printQueueItemRepository.findAllByCompanyIdAndStatusOrderByPositionAscCreateDtAsc(10L, StatusCd.ACTIVE))
                .thenReturn(List.of(insideJob, outsideJob));

        MakerStatsResponse stats = service.getStats(
                StatsRange.LAST_30_DAYS,
                LocalDate.of(2026, 10, 10),
                LocalDate.of(2026, 10, 14)
        );

        assertEquals(1, stats.totalPrints());
        assertEquals(new BigDecimal("1.00"), stats.totalPrintHours());
        assertEquals(5, stats.dailyTrend().size());
    }

    @Test
    void getStatsHandlesEmptyHistoryWithoutDivisionByZero() {
        when(makerCompanyResolver.requireActiveMakerCompany()).thenReturn(company);
        when(assetRepository.findAllByCompanyIdAndStatus(10L, StatusCd.ACTIVE)).thenReturn(List.of(printerA));
        when(filamentRepository.findAllByCompanyIdAndStatus(10L, StatusCd.ACTIVE)).thenReturn(List.of());
        when(printQueueItemRepository.findAllByCompanyIdAndStatusOrderByPositionAscCreateDtAsc(10L, StatusCd.ACTIVE))
                .thenReturn(List.of());

        MakerStatsResponse stats = service.getStats(StatsRange.ALL, null, null);

        assertEquals(0, stats.totalPrints());
        assertEquals(new BigDecimal("0.00"), stats.successRatePercent());
        assertEquals(new BigDecimal("0.00"), stats.totalPrintHours());
        assertTrue(stats.materialBreakdown().isEmpty());
        assertTrue(stats.dailyTrend().isEmpty());
        assertTrue(stats.heatmap().isEmpty());
        assertEquals(1, stats.printerUtilization().size());
        assertEquals(new BigDecimal("0.00"), stats.printerUtilization().getFirst().utilizationPercent());
    }
}
