package org.imprime.ai.api.quote;

import java.math.BigDecimal;
import java.util.List;

public record QuoteCalculationResult(
        boolean valid,
        EffectiveParameters effective,
        List<QuotePartResult> parts,
        BreakdownSummary breakdown,
        List<FilamentBreakdown> filaments,
        List<QuoteValidationWarning> warnings
) {
    public record EffectiveParameters(
            int quantity,
            String inputScope,
            Integer unitsPerPlate,
            int estimatedPlates,
            BigDecimal unitPrintHours,
            BigDecimal totalPrintHours,
            BigDecimal unitWeightGrams,
            BigDecimal totalWeightGrams,
            BigDecimal powerWatts,
            String powerSource,
            BigDecimal unitEnergyKwh,
            BigDecimal totalEnergyKwh,
            BigDecimal hourlyDepreciationRate,
            String depreciationSource,
            boolean requiresLabor,
            int effectiveLaborTimeMinutes,
            BigDecimal failureRatePercent,
            BigDecimal profitMarginPercent,
            BigDecimal additionalFeePercent,
            BigDecimal discountPercent,
            String activeProfitStrategy,
            String extraCostsScope
    ) {
    }

    public record BreakdownSummary(
            BreakdownAmounts total,
            BreakdownAmounts unit,
            BigDecimal realProfitMarginOnPricePercent,
            BigDecimal realMarkupOnCostPercent
    ) {
    }

    public record BreakdownAmounts(
            BigDecimal materialCost,
            BigDecimal energyCost,
            BigDecimal depreciationCost,
            BigDecimal directPrintCost,
            BigDecimal failureReserveCost,
            BigDecimal laborCost,
            BigDecimal packagingCost,
            BigDecimal extraCosts,
            BigDecimal productionCost,
            BigDecimal grossPriceBeforeDiscount,
            BigDecimal discountAmount,
            BigDecimal feesCost,
            BigDecimal suggestedFinalPrice,
            BigDecimal minimumBreakEvenPrice,
            BigDecimal estimatedNetProfit
    ) {
    }

    public record FilamentBreakdown(
            String entryId,
            String label,
            String mode,
            BigDecimal costPerKg,
            BigDecimal costPerGram,
            BigDecimal unitWeightGrams,
            BigDecimal totalWeightGrams,
            BigDecimal unitCost,
            BigDecimal totalCost,
            BigDecimal availableStockGrams,
            boolean exceedsStock
    ) {
    }
}
