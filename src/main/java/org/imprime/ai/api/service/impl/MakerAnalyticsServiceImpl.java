package org.imprime.ai.api.service.impl;

import lombok.RequiredArgsConstructor;
import org.imprime.ai.api.http.response.maker.ActivityHeatmapPointDto;
import org.imprime.ai.api.http.response.maker.DailyPrintTrendDto;
import org.imprime.ai.api.http.response.maker.MakerStatsResponse;
import org.imprime.ai.api.http.response.maker.MaterialBreakdownDto;
import org.imprime.ai.api.http.response.maker.PrinterUtilizationDto;
import org.imprime.ai.api.model.Asset;
import org.imprime.ai.api.model.Company;
import org.imprime.ai.api.model.Filament;
import org.imprime.ai.api.model.PrintQueueItem;
import org.imprime.ai.api.model.enums.MaterialType;
import org.imprime.ai.api.model.enums.MessageCd;
import org.imprime.ai.api.model.enums.PrintJobStatus;
import org.imprime.ai.api.model.enums.PrinterStatus;
import org.imprime.ai.api.model.enums.StatsRange;
import org.imprime.ai.api.model.enums.StatusCd;
import org.imprime.ai.api.model.exception.BadRequestException;
import org.imprime.ai.api.repo.db.AssetRepository;
import org.imprime.ai.api.repo.db.FilamentRepository;
import org.imprime.ai.api.repo.db.PrintQueueItemRepository;
import org.imprime.ai.api.service.MakerAnalyticsService;
import org.imprime.ai.api.service.MakerCompanyResolver;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class MakerAnalyticsServiceImpl implements MakerAnalyticsService {
    private static final int DEFAULT_POWER_WATTS = 300;
    private static final long MAX_CUSTOM_RANGE_DAYS = 730L;
    private static final BigDecimal ZERO_DEC = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
    private static final BigDecimal ONE_HUNDRED = new BigDecimal("100");
    private static final BigDecimal MINUTES_PER_HOUR = new BigDecimal("60");
    private static final BigDecimal GRAMS_PER_KG = new BigDecimal("1000");
    private static final BigDecimal WATTS_PER_KW = new BigDecimal("1000");

    private static final Set<PrintJobStatus> TERMINAL_JOB_STATUSES = EnumSet.of(
            PrintJobStatus.COMPLETED,
            PrintJobStatus.FAILED,
            PrintJobStatus.CANCELLED
    );

    private static final Map<MaterialType, String> DEFAULT_MATERIAL_COLORS = Map.of(
            MaterialType.PLA, "#3b82f6",
            MaterialType.PETG, "#10b981",
            MaterialType.ABS, "#f59e0b",
            MaterialType.ASA, "#ef4444",
            MaterialType.TPU, "#06b6d4",
            MaterialType.RESIN, "#8b5cf6",
            MaterialType.NYLON, "#6366f1",
            MaterialType.CARBON_FIBER, "#334155",
            MaterialType.OTHER, "#64748b"
    );

    private final MakerCompanyResolver makerCompanyResolver;
    private final PrintQueueItemRepository printQueueItemRepository;
    private final AssetRepository assetRepository;
    private final FilamentRepository filamentRepository;

    @Override
    public MakerStatsResponse getStats(StatsRange range, LocalDate from, LocalDate to) {
        StatsRange effectiveRange = range != null ? range : StatsRange.LAST_30_DAYS;
        LocalDate today = LocalDate.now(ZoneOffset.UTC);

        if (from != null && to != null && from.isAfter(to)) {
            throw new BadRequestException(MessageCd.VALIDATION_ERROR);
        }

        DateWindow window = resolveWindow(effectiveRange, from, to, today);
        if (window.start() != null && window.start().isAfter(window.end())) {
            throw new BadRequestException(MessageCd.VALIDATION_ERROR);
        }
        if (window.start() != null && ChronoUnit.DAYS.between(window.start(), window.end()) > MAX_CUSTOM_RANGE_DAYS) {
            throw new BadRequestException(MessageCd.VALIDATION_ERROR);
        }

        Company company = makerCompanyResolver.requireActiveMakerCompany();
        List<Asset> printers = assetRepository.findAllByCompanyIdAndStatus(company.getId(), StatusCd.ACTIVE);
        Map<Long, Asset> printersById = printers.stream()
                .collect(Collectors.toMap(Asset::getId, a -> a, (a, b) -> a));

        Map<Long, Filament> filamentsById = filamentRepository.findAllByCompanyIdAndStatus(company.getId(), StatusCd.ACTIVE)
                .stream()
                .collect(Collectors.toMap(Filament::getId, f -> f, (a, b) -> a));

        List<PrintQueueItem> allJobs = printQueueItemRepository
                .findAllByCompanyIdAndStatusOrderByPositionAscCreateDtAsc(company.getId(), StatusCd.ACTIVE);

        int successfulPrints = 0;
        int failedPrints = 0;
        int cancelledPrints = 0;
        int jobsWithDurationCount = 0;
        BigDecimal totalPrintMinutes = BigDecimal.ZERO;
        BigDecimal totalFilamentGrams = BigDecimal.ZERO;
        BigDecimal totalEnergyKwh = BigDecimal.ZERO;
        LocalDate earliestJobDate = null;

        Map<MaterialType, MaterialAccumulator> materialStats = new EnumMap<>(MaterialType.class);
        Map<Long, PrinterAccumulator> printerStats = new HashMap<>();
        Map<LocalDate, DailyAccumulator> dailyStats = new HashMap<>();

        for (PrintQueueItem job : allJobs) {
            if (job == null || job.getJobStatus() == null || !TERMINAL_JOB_STATUSES.contains(job.getJobStatus())) {
                continue;
            }
            LocalDate jobDate = resolveJobDate(job, today);
            if (!isWithinWindow(jobDate, window)) {
                continue;
            }

            if (earliestJobDate == null || jobDate.isBefore(earliestJobDate)) {
                earliestJobDate = jobDate;
            }

            PrintJobStatus status = job.getJobStatus();
            if (PrintJobStatus.COMPLETED.equals(status)) {
                successfulPrints++;
            } else if (PrintJobStatus.FAILED.equals(status)) {
                failedPrints++;
            } else if (PrintJobStatus.CANCELLED.equals(status)) {
                cancelledPrints++;
                continue;
            }

            BigDecimal jobMinutes = resolveExecutedMinutes(job, status);
            BigDecimal jobHours = jobMinutes.divide(MINUTES_PER_HOUR, 6, RoundingMode.HALF_UP);
            if (jobMinutes.compareTo(BigDecimal.ZERO) > 0) {
                jobsWithDurationCount++;
                totalPrintMinutes = totalPrintMinutes.add(jobMinutes);
            }

            Asset printer = job.getAssetId() != null ? printersById.get(job.getAssetId()) : null;
            int watts = printer != null && printer.getPowerWatts() != null && printer.getPowerWatts() > 0
                    ? printer.getPowerWatts()
                    : DEFAULT_POWER_WATTS;
            BigDecimal jobKwh = jobHours.multiply(BigDecimal.valueOf(watts))
                    .divide(WATTS_PER_KW, 6, RoundingMode.HALF_UP);
            totalEnergyKwh = totalEnergyKwh.add(jobKwh);

            BigDecimal jobGrams = resolveConsumedGrams(job, status);
            if (jobGrams.compareTo(BigDecimal.ZERO) > 0) {
                totalFilamentGrams = totalFilamentGrams.add(jobGrams);
                Filament filament = job.getFilamentId() != null ? filamentsById.get(job.getFilamentId()) : null;
                MaterialType material = job.getMaterial() != null
                        ? job.getMaterial()
                        : (filament != null && filament.getMaterial() != null ? filament.getMaterial() : MaterialType.OTHER);
                String colorHex = resolveColorHex(job.getColorHex(), filament, material);
                BigDecimal jobMaterialCost = (filament != null && filament.getCostPerKg() != null)
                        ? jobGrams.multiply(filament.getCostPerKg()).divide(GRAMS_PER_KG, 6, RoundingMode.HALF_UP)
                        : BigDecimal.ZERO;
                materialStats.computeIfAbsent(material, k -> new MaterialAccumulator(colorHex))
                        .add(jobGrams, jobMaterialCost);
            }

            if (job.getAssetId() != null) {
                printerStats.computeIfAbsent(job.getAssetId(), k -> new PrinterAccumulator())
                        .add(jobHours);
            }

            dailyStats.computeIfAbsent(jobDate, k -> new DailyAccumulator())
                    .add(jobHours, jobGrams);
        }

        int totalPrints = successfulPrints + failedPrints + cancelledPrints;
        BigDecimal totalPrintHours = totalPrintMinutes.divide(MINUTES_PER_HOUR, 2, RoundingMode.HALF_UP);
        BigDecimal successRatePercent = totalPrints > 0
                ? BigDecimal.valueOf(successfulPrints).multiply(ONE_HUNDRED)
                .divide(BigDecimal.valueOf(totalPrints), 2, RoundingMode.HALF_UP)
                : ZERO_DEC;

        int avgDenominator = successfulPrints > 0 ? successfulPrints : jobsWithDurationCount;
        BigDecimal avgPrintDurationHours = avgDenominator > 0
                ? totalPrintMinutes.divide(MINUTES_PER_HOUR, 6, RoundingMode.HALF_UP)
                .divide(BigDecimal.valueOf(avgDenominator), 2, RoundingMode.HALF_UP)
                : ZERO_DEC;

        BigDecimal kwhRate = company.getKwhRate() != null
                ? company.getKwhRate()
                : MakerFinanceServiceImpl.DEFAULT_KWH_RATE;
        BigDecimal scaledEnergyKwh = totalEnergyKwh.setScale(2, RoundingMode.HALF_UP);
        BigDecimal estimatedEnergyCost = totalEnergyKwh.multiply(kwhRate).setScale(2, RoundingMode.HALF_UP);
        BigDecimal scaledTotalGrams = totalFilamentGrams.setScale(2, RoundingMode.HALF_UP);

        long effectiveDays = resolveEffectiveWindowDays(window, earliestJobDate);
        List<MaterialBreakdownDto> materialBreakdown = buildMaterialBreakdown(materialStats, totalFilamentGrams);
        List<PrinterUtilizationDto> printerUtilization = buildPrinterUtilization(printers, printerStats, effectiveDays);
        List<LocalDate> seriesDates = resolveSeriesDates(window, earliestJobDate);
        List<DailyPrintTrendDto> dailyTrend = buildDailyTrend(seriesDates, dailyStats);
        List<ActivityHeatmapPointDto> heatmap = buildHeatmap(seriesDates, dailyStats);

        return new MakerStatsResponse(
                effectiveRange,
                totalPrintHours,
                totalPrints,
                successfulPrints,
                failedPrints,
                cancelledPrints,
                successRatePercent,
                scaledTotalGrams,
                scaledEnergyKwh,
                estimatedEnergyCost,
                avgPrintDurationHours,
                materialBreakdown,
                printerUtilization,
                dailyTrend,
                heatmap
        );
    }

    private DateWindow resolveWindow(StatsRange range, LocalDate from, LocalDate to, LocalDate today) {
        if (from != null || to != null) {
            LocalDate end = to != null ? to : today;
            LocalDate start = from != null ? from : resolveStartFromEnd(range, end);
            return new DateWindow(start, end);
        }
        return switch (range) {
            case LAST_7_DAYS -> new DateWindow(today.minusDays(6), today);
            case LAST_30_DAYS -> new DateWindow(today.minusDays(29), today);
            case LAST_90_DAYS -> new DateWindow(today.minusDays(89), today);
            case ALL -> new DateWindow(null, today);
        };
    }

    private LocalDate resolveStartFromEnd(StatsRange range, LocalDate end) {
        return switch (range) {
            case LAST_7_DAYS -> end.minusDays(6);
            case LAST_30_DAYS -> end.minusDays(29);
            case LAST_90_DAYS -> end.minusDays(89);
            case ALL -> null;
        };
    }

    private boolean isWithinWindow(LocalDate jobDate, DateWindow window) {
        if (window.start() != null && jobDate.isBefore(window.start())) {
            return false;
        }
        return !jobDate.isAfter(window.end());
    }

    private LocalDate resolveJobDate(PrintQueueItem job, LocalDate fallback) {
        Instant ts = job.getUpdateDt() != null ? job.getUpdateDt() : job.getCreateDt();
        return ts != null ? ts.atZone(ZoneOffset.UTC).toLocalDate() : fallback;
    }

    private BigDecimal resolveExecutedMinutes(PrintQueueItem job, PrintJobStatus status) {
        if (job.getEstimatedTimeMinutes() == null || job.getEstimatedTimeMinutes() <= 0) {
            return BigDecimal.ZERO;
        }
        if (PrintJobStatus.COMPLETED.equals(status)) {
            int copiesFactor = resolveCompletedCopiesFactor(job);
            return BigDecimal.valueOf((long) job.getEstimatedTimeMinutes() * copiesFactor);
        }
        if (PrintJobStatus.FAILED.equals(status) && job.getProgressPercent() != null && job.getProgressPercent() > 0) {
            int boundedProgress = Math.min(100, job.getProgressPercent());
            return BigDecimal.valueOf(job.getEstimatedTimeMinutes())
                    .multiply(BigDecimal.valueOf(boundedProgress))
                    .divide(ONE_HUNDRED, 4, RoundingMode.HALF_UP);
        }
        return BigDecimal.ZERO;
    }

    private BigDecimal resolveConsumedGrams(PrintQueueItem job, PrintJobStatus status) {
        if (job.getEstimatedWeightGrams() == null || job.getEstimatedWeightGrams().compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }
        if (PrintJobStatus.COMPLETED.equals(status)) {
            return job.getEstimatedWeightGrams().multiply(BigDecimal.valueOf(resolveCompletedCopiesFactor(job)));
        }
        if (PrintJobStatus.FAILED.equals(status) && job.getProgressPercent() != null && job.getProgressPercent() > 0) {
            return job.getEstimatedWeightGrams();
        }
        return BigDecimal.ZERO;
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

    private String resolveColorHex(String jobColorHex, Filament filament, MaterialType material) {
        if (jobColorHex != null && !jobColorHex.isBlank()) {
            return jobColorHex;
        }
        if (filament != null && filament.getColorHex() != null && !filament.getColorHex().isBlank()) {
            return filament.getColorHex();
        }
        return material != null ? DEFAULT_MATERIAL_COLORS.getOrDefault(material, "#64748b") : "#64748b";
    }

    private long resolveEffectiveWindowDays(DateWindow window, LocalDate earliestJobDate) {
        if (window.start() != null) {
            return ChronoUnit.DAYS.between(window.start(), window.end()) + 1L;
        }
        if (earliestJobDate == null || earliestJobDate.isAfter(window.end())) {
            return 0L;
        }
        return ChronoUnit.DAYS.between(earliestJobDate, window.end()) + 1L;
    }

    private List<MaterialBreakdownDto> buildMaterialBreakdown(
            Map<MaterialType, MaterialAccumulator> materialStats,
            BigDecimal totalFilamentGrams) {
        if (materialStats.isEmpty() || totalFilamentGrams.compareTo(BigDecimal.ZERO) <= 0) {
            return List.of();
        }
        return materialStats.entrySet().stream()
                .sorted((a, b) -> b.getValue().grams.compareTo(a.getValue().grams))
                .map(entry -> {
                    BigDecimal grams = entry.getValue().grams.setScale(2, RoundingMode.HALF_UP);
                    BigDecimal pct = entry.getValue().grams.multiply(ONE_HUNDRED)
                            .divide(totalFilamentGrams, 2, RoundingMode.HALF_UP);
                    BigDecimal cost = entry.getValue().cost.setScale(2, RoundingMode.HALF_UP);
                    return new MaterialBreakdownDto(entry.getKey(), grams, pct, entry.getValue().colorHex, cost);
                })
                .toList();
    }

    private List<PrinterUtilizationDto> buildPrinterUtilization(
            List<Asset> printers,
            Map<Long, PrinterAccumulator> printerStats,
            long effectiveDays) {
        BigDecimal capacityHours = effectiveDays > 0
                ? BigDecimal.valueOf(effectiveDays * 24L)
                : BigDecimal.ZERO;

        return printers.stream()
                .map(printer -> {
                    PrinterAccumulator acc = printerStats.get(printer.getId());
                    BigDecimal rawHours = acc != null ? acc.hours : BigDecimal.ZERO;
                    int printsCount = acc != null ? acc.printsCount : 0;
                    BigDecimal utilization = capacityHours.compareTo(BigDecimal.ZERO) > 0 && rawHours.compareTo(BigDecimal.ZERO) > 0
                            ? rawHours.multiply(ONE_HUNDRED)
                            .divide(capacityHours, 2, RoundingMode.HALF_UP)
                            .min(ONE_HUNDRED.setScale(2, RoundingMode.HALF_UP))
                            : ZERO_DEC;

                    return new PrinterUtilizationDto(
                            printer.getGuid(),
                            printer.getName(),
                            printer.getModel(),
                            printer.getPrinterStatus() != null ? printer.getPrinterStatus() : PrinterStatus.ONLINE,
                            rawHours.setScale(2, RoundingMode.HALF_UP),
                            printsCount,
                            utilization
                    );
                })
                .sorted(Comparator
                        .comparing(PrinterUtilizationDto::hours).reversed()
                        .thenComparing(PrinterUtilizationDto::name, Comparator.nullsLast(String::compareToIgnoreCase)))
                .toList();
    }

    private List<LocalDate> resolveSeriesDates(DateWindow window, LocalDate earliestJobDate) {
        LocalDate start = window.start();
        if (start == null) {
            if (earliestJobDate == null || earliestJobDate.isAfter(window.end())) {
                return List.of();
            }
            LocalDate maxSupportedStart = window.end().minusDays(MAX_CUSTOM_RANGE_DAYS - 1L);
            start = earliestJobDate.isBefore(maxSupportedStart) ? maxSupportedStart : earliestJobDate;
        }
        long dayCount = ChronoUnit.DAYS.between(start, window.end()) + 1L;
        List<LocalDate> dates = new ArrayList<>((int) dayCount);
        for (LocalDate d = start; !d.isAfter(window.end()); d = d.plusDays(1)) {
            dates.add(d);
        }
        return dates;
    }

    private List<DailyPrintTrendDto> buildDailyTrend(
            List<LocalDate> dates,
            Map<LocalDate, DailyAccumulator> dailyStats) {
        return dates.stream()
                .map(date -> {
                    DailyAccumulator acc = dailyStats.get(date);
                    return new DailyPrintTrendDto(
                            date,
                            acc != null ? acc.hours.setScale(2, RoundingMode.HALF_UP) : ZERO_DEC,
                            acc != null ? acc.printsCount : 0,
                            acc != null ? acc.grams.setScale(2, RoundingMode.HALF_UP) : ZERO_DEC
                    );
                })
                .toList();
    }

    private List<ActivityHeatmapPointDto> buildHeatmap(
            List<LocalDate> dates,
            Map<LocalDate, DailyAccumulator> dailyStats) {
        double maxScore = dates.stream()
                .map(dailyStats::get)
                .filter(acc -> acc != null && acc.printsCount > 0)
                .mapToDouble(acc -> activityScore(acc.printsCount, acc.hours))
                .max()
                .orElse(0.0);

        return dates.stream()
                .map(date -> {
                    DailyAccumulator acc = dailyStats.get(date);
                    int printsCount = acc != null ? acc.printsCount : 0;
                    BigDecimal hours = acc != null ? acc.hours.setScale(2, RoundingMode.HALF_UP) : ZERO_DEC;
                    int intensity = computeIntensityLevel(printsCount, hours, maxScore);
                    return new ActivityHeatmapPointDto(date, printsCount, hours, intensity);
                })
                .toList();
    }

    private double activityScore(int printsCount, BigDecimal hours) {
        return printsCount * 2.0 + (hours != null ? hours.doubleValue() : 0.0);
    }

    private int computeIntensityLevel(int printsCount, BigDecimal hours, double maxScore) {
        if (printsCount <= 0 && (hours == null || hours.compareTo(BigDecimal.ZERO) <= 0)) {
            return 0;
        }
        if (maxScore <= 0.0) {
            return 1;
        }
        double ratio = activityScore(printsCount, hours) / maxScore;
        if (ratio > 0.75) {
            return 4;
        }
        if (ratio > 0.50) {
            return 3;
        }
        if (ratio > 0.25) {
            return 2;
        }
        return 1;
    }

    private record DateWindow(LocalDate start, LocalDate end) {
    }

    private static final class MaterialAccumulator {
        private final String colorHex;
        private BigDecimal grams = BigDecimal.ZERO;
        private BigDecimal cost = BigDecimal.ZERO;

        private MaterialAccumulator(String colorHex) {
            this.colorHex = colorHex;
        }

        private void add(BigDecimal addedGrams, BigDecimal addedCost) {
            this.grams = this.grams.add(addedGrams);
            this.cost = this.cost.add(addedCost);
        }
    }

    private static final class PrinterAccumulator {
        private BigDecimal hours = BigDecimal.ZERO;
        private int printsCount = 0;

        private void add(BigDecimal addedHours) {
            this.hours = this.hours.add(addedHours);
            this.printsCount++;
        }
    }

    private static final class DailyAccumulator {
        private BigDecimal hours = BigDecimal.ZERO;
        private BigDecimal grams = BigDecimal.ZERO;
        private int printsCount = 0;

        private void add(BigDecimal addedHours, BigDecimal addedGrams) {
            this.hours = this.hours.add(addedHours);
            this.grams = this.grams.add(addedGrams);
            this.printsCount++;
        }
    }
}
