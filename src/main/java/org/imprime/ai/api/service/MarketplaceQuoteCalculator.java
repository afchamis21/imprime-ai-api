package org.imprime.ai.api.service;

import org.imprime.ai.api.model.MakerPricingProfile;
import org.imprime.ai.api.model.enums.MaterialType;
import org.imprime.ai.api.model.enums.PrinterSpeedTier;
import org.imprime.ai.api.model.enums.SetupFeeScope;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Pure deterministic calculator shared between instant marketplace quoting and
 * server-side headless CLI checkout validation.
 */
@Component
public class MarketplaceQuoteCalculator {

    private static final int INTERNAL_SCALE = 6;
    private static final BigDecimal SECONDS_PER_HOUR = BigDecimal.valueOf(3600);
    private static final BigDecimal ONE = BigDecimal.ONE;

    public record PlateQuoteInput(
            int plateNumber,
            String plateName,
            MaterialType material,
            int quantity,
            BigDecimal modelWeightGrams,
            BigDecimal supportWeightGrams,
            int basePrintTimeSeconds,
            @Nullable String finish,
            @Nullable String urgency,
            @Nullable Long matchedPrinterAssetId,
            @Nullable String matchedPrinterGuid,
            @Nullable String matchedPrinterName,
            @Nullable String matchedPrinterModel,
            PrinterSpeedTier speedTier,
            BigDecimal speedMultiplier
    ) {
    }

    public record CalculatedPlateQuote(
            int plateNumber,
            String plateName,
            MaterialType material,
            int quantity,
            @Nullable Long printerAssetId,
            @Nullable String printerGuid,
            @Nullable String printerName,
            String printerModel,
            PrinterSpeedTier speedTier,
            BigDecimal speedMultiplier,
            BigDecimal unitTotalWeightGrams,
            BigDecimal totalWeightGrams,
            int estimatedPrintTimeSeconds,
            BigDecimal materialCostBrl,
            BigDecimal machineCostBrl,
            BigDecimal supportCostBrl,
            BigDecimal setupFeeBrl,
            BigDecimal finishAndUrgencyAdjustmentBrl,
            BigDecimal totalPriceBrl
    ) {
    }

    public record CalculatedOrderQuote(
            BigDecimal totalPriceBrl,
            boolean minimumOrderFeeApplied,
            BigDecimal materialCostBrl,
            BigDecimal machineCostBrl,
            BigDecimal supportCostBrl,
            BigDecimal setupFeeBrl,
            BigDecimal finishAndUrgencyAdjustmentBrl,
            BigDecimal minimumOrderDeltaBrl,
            int totalEstimatedPrintTimeSeconds,
            int estimatedLeadTimeDays,
            List<CalculatedPlateQuote> plateQuotes
    ) {
    }

    public CalculatedOrderQuote calculate(
            MakerPricingProfile profile,
            Map<MaterialType, BigDecimal> materialPricePerGram,
            List<PlateQuoteInput> plates
    ) {
        BigDecimal hourlyRate = nonNegativeOrDefault(profile.getHourlyRateBrl(), new BigDecimal("4.50"));
        BigDecimal minOrderFee = nonNegativeOrDefault(profile.getMinimumOrderFeeBrl(), BigDecimal.ZERO);
        BigDecimal setupFee = nonNegativeOrDefault(profile.getSetupFeeBrl(), BigDecimal.ZERO);
        SetupFeeScope setupScope = profile.getSetupFeeScope() != null ? profile.getSetupFeeScope() : SetupFeeScope.PER_PLATE;
        BigDecimal supportRemovalFee = nonNegativeOrDefault(profile.getSupportRemovalFeeBrl(), BigDecimal.ZERO);
        BigDecimal supportPricePerGram = nonNegativeOrDefault(profile.getSupportPricePerGramBrl(), BigDecimal.ZERO);
        int baseLeadTimeDays = profile.getLeadTimeDays() != null ? Math.max(1, profile.getLeadTimeDays()) : 3;

        BigDecimal rawTotalSum = BigDecimal.ZERO;
        BigDecimal materialSum = BigDecimal.ZERO;
        BigDecimal machineSum = BigDecimal.ZERO;
        BigDecimal supportSum = BigDecimal.ZERO;
        BigDecimal setupSum = BigDecimal.ZERO;
        BigDecimal adjustmentSum = BigDecimal.ZERO;
        int totalEstimatedSeconds = 0;

        List<CalculatedPlateQuote> plateQuotes = new ArrayList<>(plates.size());

        for (PlateQuoteInput plate : plates) {
            int qty = Math.max(1, plate.quantity());
            BigDecimal qtyBd = BigDecimal.valueOf(qty);
            BigDecimal modelW = nonNegativeOrDefault(plate.modelWeightGrams(), BigDecimal.ZERO);
            BigDecimal supportW = nonNegativeOrDefault(plate.supportWeightGrams(), BigDecimal.ZERO);
            BigDecimal unitTotalWeight = modelW.add(supportW);
            BigDecimal totalWeight = unitTotalWeight.multiply(qtyBd);

            BigDecimal speedMult = plate.speedMultiplier() != null && plate.speedMultiplier().signum() > 0
                    ? plate.speedMultiplier()
                    : PrinterSpeedTier.STANDARD.getDefaultMultiplier();
            PrinterSpeedTier tier = plate.speedTier() != null
                    ? plate.speedTier()
                    : PrinterSpeedTier.fromMultiplier(speedMult);

            int unitBaseSeconds = Math.max(1, plate.basePrintTimeSeconds());
            int unitEstimatedSeconds = Math.max(
                    1,
                    BigDecimal.valueOf(unitBaseSeconds)
                            .multiply(speedMult)
                            .setScale(0, RoundingMode.CEILING)
                            .intValueExact()
            );
            int plateEstimatedSeconds = Math.multiplyExact(unitEstimatedSeconds, qty);
            totalEstimatedSeconds = Math.addExact(totalEstimatedSeconds, plateEstimatedSeconds);

            BigDecimal estimatedHours = BigDecimal.valueOf(plateEstimatedSeconds)
                    .divide(SECONDS_PER_HOUR, INTERNAL_SCALE, RoundingMode.HALF_UP);

            BigDecimal pricePerGram = materialPricePerGram.get(plate.material());
            if (pricePerGram == null || pricePerGram.signum() < 0) {
                throw new IllegalArgumentException("Missing enabled price per gram for material: " + plate.material());
            }

            BigDecimal materialCost = totalWeight.multiply(pricePerGram);
            BigDecimal machineCost = estimatedHours.multiply(hourlyRate);
            BigDecimal supportCost = supportW.signum() > 0
                    ? supportRemovalFee.multiply(qtyBd).add(supportW.multiply(qtyBd).multiply(supportPricePerGram))
                    : BigDecimal.ZERO;

            BigDecimal plateSetupCost = switch (setupScope) {
                case PER_PLATE -> setupFee;
                case PER_UNIT -> setupFee.multiply(qtyBd);
                case PER_ORDER -> BigDecimal.ZERO;
            };

            BigDecimal baseCost = materialCost.add(machineCost).add(supportCost).add(plateSetupCost);
            BigDecimal finishMult = resolveFinishMultiplier(profile, plate.finish());
            BigDecimal urgencyMult = resolveUrgencyMultiplier(profile, plate.urgency());
            BigDecimal combinedMult = finishMult.multiply(urgencyMult);

            BigDecimal plateSubtotalRaw = baseCost.multiply(combinedMult);
            BigDecimal plateAdjustment = plateSubtotalRaw.subtract(baseCost);

            rawTotalSum = rawTotalSum.add(plateSubtotalRaw);
            materialSum = materialSum.add(materialCost);
            machineSum = machineSum.add(machineCost);
            supportSum = supportSum.add(supportCost);
            setupSum = setupSum.add(plateSetupCost);
            adjustmentSum = adjustmentSum.add(plateAdjustment);

            plateQuotes.add(new CalculatedPlateQuote(
                    plate.plateNumber(),
                    plate.plateName(),
                    plate.material(),
                    qty,
                    plate.matchedPrinterAssetId(),
                    plate.matchedPrinterGuid(),
                    plate.matchedPrinterName(),
                    plate.matchedPrinterModel() != null ? plate.matchedPrinterModel() : "Impressora compatível",
                    tier,
                    speedMult.setScale(2, RoundingMode.HALF_UP),
                    scale2(unitTotalWeight),
                    scale2(totalWeight),
                    plateEstimatedSeconds,
                    scale2(materialCost),
                    scale2(machineCost),
                    scale2(supportCost),
                    scale2(plateSetupCost),
                    scale2(plateAdjustment),
                    scale2(plateSubtotalRaw)
            ));
        }

        if (setupScope == SetupFeeScope.PER_ORDER) {
            rawTotalSum = rawTotalSum.add(setupFee);
            setupSum = setupSum.add(setupFee);
        }

        BigDecimal roundedRawTotal = scale2(rawTotalSum);
        BigDecimal roundedMinOrder = scale2(minOrderFee);
        boolean minApplied = roundedMinOrder.compareTo(roundedRawTotal) > 0;
        BigDecimal totalPrice = minApplied ? roundedMinOrder : roundedRawTotal;
        BigDecimal minDelta = minApplied ? totalPrice.subtract(roundedRawTotal) : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);

        double totalPrintHours = totalEstimatedSeconds / 3600.0d;
        int productionDaysByPrintHours = (int) Math.ceil(totalPrintHours / 12.0d);
        int estimatedLeadTimeDays = Math.max(baseLeadTimeDays, productionDaysByPrintHours + 1);

        return new CalculatedOrderQuote(
                totalPrice,
                minApplied,
                scale2(materialSum),
                scale2(machineSum),
                scale2(supportSum),
                scale2(setupSum),
                scale2(adjustmentSum),
                minDelta,
                totalEstimatedSeconds,
                estimatedLeadTimeDays,
                plateQuotes
        );
    }

    private static BigDecimal resolveFinishMultiplier(MakerPricingProfile profile, @Nullable String finish) {
        if (finish == null || finish.isBlank()) {
            return ONE;
        }
        String normalized = finish.trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "sanded" -> atLeastOne(profile.getFinishSandedMultiplier(), new BigDecimal("1.15"));
            case "vapor_smoothed", "vapor" -> atLeastOne(profile.getFinishVaporMultiplier(), new BigDecimal("1.25"));
            case "painted" -> atLeastOne(profile.getFinishPaintedMultiplier(), new BigDecimal("1.35"));
            default -> ONE;
        };
    }

    private static BigDecimal resolveUrgencyMultiplier(MakerPricingProfile profile, @Nullable String urgency) {
        if ("express".equalsIgnoreCase(urgency != null ? urgency.trim() : null)) {
            return atLeastOne(profile.getExpressMultiplier(), new BigDecimal("1.20"));
        }
        return ONE;
    }

    private static BigDecimal nonNegativeOrDefault(@Nullable BigDecimal value, BigDecimal fallback) {
        if (value == null || value.signum() < 0) {
            return fallback;
        }
        return value;
    }

    private static BigDecimal atLeastOne(@Nullable BigDecimal value, BigDecimal fallback) {
        BigDecimal candidate = value != null ? value : fallback;
        return candidate.compareTo(ONE) >= 0 ? candidate : ONE;
    }

    private static BigDecimal scale2(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP);
    }
}
