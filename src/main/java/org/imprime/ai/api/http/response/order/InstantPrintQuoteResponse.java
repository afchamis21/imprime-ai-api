package org.imprime.ai.api.http.response.order;

import org.imprime.ai.api.model.enums.PrinterSpeedTier;

import java.math.BigDecimal;
import java.util.List;

public record InstantPrintQuoteResponse(
        String currency,
        String referenceEngineId,
        String referenceProfileId,
        DnaSummaryDto dnaSummary,
        int evaluatedMakersCount,
        int eligibleMakersCount,
        List<MakerInstantQuoteItem> quotes
) {
    public record BoundingBoxMmDto(
            BigDecimal x,
            BigDecimal y,
            BigDecimal z
    ) {
    }

    public record DnaSummaryDto(
            int plateCount,
            int totalUnits,
            BigDecimal totalModelWeightGrams,
            BigDecimal totalSupportWeightGrams,
            BigDecimal totalWeightGrams,
            int totalBasePrintTimeSeconds,
            BoundingBoxMmDto maxBoundingBoxMm
    ) {
    }

    public record MatchedPrinterDto(
            String printerGuid,
            String name,
            String model,
            PrinterSpeedTier speedTier,
            BigDecimal speedMultiplier,
            BoundingBoxMmDto maxBuildVolumeMm
    ) {
    }

    public record PriceBreakdownDto(
            BigDecimal materialCostBrl,
            BigDecimal machineCostBrl,
            BigDecimal supportCostBrl,
            BigDecimal setupFeeBrl,
            BigDecimal finishAndUrgencyAdjustmentBrl,
            BigDecimal minimumOrderDeltaBrl
    ) {
    }

    public record PlateQuoteItemDto(
            int plateNumber,
            String plateName,
            String material,
            int quantity,
            String printerModel,
            PrinterSpeedTier speedTier,
            BigDecimal speedMultiplier,
            int estimatedPrintTimeSeconds,
            BigDecimal totalPriceBrl
    ) {
    }

    public record MakerInstantQuoteItem(
            String makerGuid,
            String makerSlug,
            String makerName,
            String avatarUrl,
            boolean verified,
            boolean online,
            BigDecimal rating,
            int reviewsCount,
            int printsDone,
            String city,
            String state,
            Double distanceKm,
            MatchedPrinterDto matchedPrinter,
            int estimatedPrintTimeSeconds,
            int leadTimeDays,
            BigDecimal totalPriceBrl,
            boolean minimumOrderFeeApplied,
            PriceBreakdownDto breakdown,
            List<PlateQuoteItemDto> plateQuotes
    ) {
    }
}
