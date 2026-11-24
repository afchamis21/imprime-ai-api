package org.imprime.ai.api.quote.calculator;

import org.imprime.ai.api.model.Filament;
import org.imprime.ai.api.model.enums.QuoteModuleType;
import org.imprime.ai.api.quote.QuoteAccumulator;
import org.imprime.ai.api.quote.QuoteCalculationResult;
import org.imprime.ai.api.quote.QuoteConfigReader;
import org.imprime.ai.api.quote.QuoteInput;
import org.imprime.ai.api.quote.QuotePartCalculator;
import org.imprime.ai.api.quote.QuotePartResult;
import org.imprime.ai.api.quote.QuoteValidationWarning;
import org.imprime.ai.api.quote.QuotingContext;
import org.imprime.ai.api.quote.ResolvedQuoteModule;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
public class MaterialQuotePartCalculator implements QuotePartCalculator {
    private static final BigDecimal DEFAULT_COST_PER_KG = new BigDecimal("120.00");

    @Override
    public QuoteModuleType supportedType() {
        return QuoteModuleType.BASE_COST;
    }

    @Override
    public QuotePartResult calculate(
            QuotingContext context,
            JsonNode effectiveConfig,
            ResolvedQuoteModule module) {
        BigDecimal defaultCostPerKg = QuoteConfigReader.getNonNegativeBigDecimal(
                effectiveConfig, "defaultCostPerKg", DEFAULT_COST_PER_KG);
        boolean checkInventoryStock = QuoteConfigReader.getBoolean(
                effectiveConfig, "checkInventoryStock", true);

        QuoteAccumulator acc = context.getAccumulator();
        List<QuoteInput.FilamentInput> entries = context.getInput().filaments() != null
                ? context.getInput().filaments()
                : List.of();

        String scope = context.getInputScope();
        BigDecimal qty = BigDecimal.valueOf(context.getQuantity());
        BigDecimal plateCap = BigDecimal.valueOf(
                Math.max(1, context.getUnitsPerPlate() != null ? context.getUnitsPerPlate() : 1)
        );

        List<ResolvedEntry> resolved = new ArrayList<>(entries.size());
        Map<String, BigDecimal> totalRequestedBySpool = new HashMap<>();

        for (QuoteInput.FilamentInput entry : entries) {
            if (entry == null) {
                continue;
            }
            String mode = entry.mode() != null ? entry.mode().trim() : "select";
            boolean selectMode = "select".equalsIgnoreCase(mode);
            Filament matched = selectMode ? context.findFilament(entry.filamentId()) : null;

            BigDecimal rawWeight = QuoteConfigReader.sanitizeNonNegative(entry.weightGrams());
            BigDecimal unitWeight;
            BigDecimal totalWeight;
            if ("total_batch".equalsIgnoreCase(scope)) {
                unitWeight = QuoteConfigReader.divide(rawWeight, qty);
                totalWeight = rawWeight;
            } else if ("per_plate".equalsIgnoreCase(scope)) {
                unitWeight = QuoteConfigReader.divide(rawWeight, plateCap);
                totalWeight = QuoteConfigReader.divide(rawWeight.multiply(qty), plateCap);
            } else {
                unitWeight = rawWeight;
                totalWeight = rawWeight.multiply(qty);
            }

            BigDecimal effectiveCostPerKg;
            if (selectMode && matched != null) {
                effectiveCostPerKg = QuoteConfigReader.sanitizeNonNegative(matched.getCostPerKg());
            } else if (entry.costPerKg() != null) {
                effectiveCostPerKg = QuoteConfigReader.sanitizeNonNegative(entry.costPerKg());
            } else {
                effectiveCostPerKg = defaultCostPerKg;
            }

            if (matched != null) {
                String spoolKey = spoolKey(matched);
                totalRequestedBySpool.merge(spoolKey, totalWeight, BigDecimal::add);
            }

            resolved.add(new ResolvedEntry(entry, mode, selectMode, matched, unitWeight, totalWeight, effectiveCostPerKg));
        }

        Set<String> warnedSpools = new HashSet<>();
        List<QuoteCalculationResult.FilamentBreakdown> filamentBreakdowns = new ArrayList<>(resolved.size());
        BigDecimal unitWeightSum = BigDecimal.ZERO;
        BigDecimal totalWeightSum = BigDecimal.ZERO;
        BigDecimal totalMaterialCostRaw = BigDecimal.ZERO;

        for (ResolvedEntry item : resolved) {
            BigDecimal costPerGram = QuoteConfigReader.divide(item.effectiveCostPerKg, QuoteConfigReader.THOUSAND);
            BigDecimal totalCost = item.totalWeight.multiply(costPerGram);
            BigDecimal unitCost = item.unitWeight.multiply(costPerGram);

            unitWeightSum = unitWeightSum.add(item.unitWeight);
            totalWeightSum = totalWeightSum.add(item.totalWeight);
            totalMaterialCostRaw = totalMaterialCostRaw.add(totalCost);

            BigDecimal availableStock = null;
            boolean exceedsStock = false;
            if (item.matched != null) {
                availableStock = QuoteConfigReader.scale2(QuoteConfigReader.sanitizeNonNegative(item.matched.getStock()));
                String spoolKey = spoolKey(item.matched);
                BigDecimal combinedRequested = totalRequestedBySpool.getOrDefault(spoolKey, item.totalWeight);
                exceedsStock = checkInventoryStock && combinedRequested.compareTo(availableStock) > 0;
                if (exceedsStock && warnedSpools.add(spoolKey)) {
                    acc.addWarning(new QuoteValidationWarning(
                            "INSUFFICIENT_FILAMENT_STOCK",
                            "warning",
                            "Estoque insuficiente de \"" + item.matched.getName() + "\": necessário "
                                    + QuoteConfigReader.formatCompactPtBr(combinedRequested, 1) + "g, disponível "
                                    + QuoteConfigReader.formatCompactPtBr(availableStock, 1) + "g."
                    ));
                }
            }

            filamentBreakdowns.add(new QuoteCalculationResult.FilamentBreakdown(
                    item.entry.entryId(),
                    buildFilamentLabel(item),
                    item.selectMode ? "select" : "manual",
                    QuoteConfigReader.scale2(item.effectiveCostPerKg),
                    QuoteConfigReader.scale(costPerGram, 4),
                    QuoteConfigReader.scale2(item.unitWeight),
                    QuoteConfigReader.scale2(item.totalWeight),
                    QuoteConfigReader.scale2(unitCost),
                    QuoteConfigReader.scale2(totalCost),
                    availableStock,
                    exceedsStock
            ));
        }

        if (totalWeightSum.compareTo(BigDecimal.ZERO) <= 0) {
            acc.addWarningOnce(new QuoteValidationWarning(
                    "ZERO_FILAMENT_WEIGHT",
                    "info",
                    "Informe o peso de filamento gasto (g) para calcular o custo de material."
            ));
        }

        acc.getFilaments().clear();
        acc.getFilaments().addAll(filamentBreakdowns);
        acc.setUnitWeightGrams(unitWeightSum);
        acc.setTotalWeightGrams(totalWeightSum);
        acc.setMaterialCostTotal(totalMaterialCostRaw);
        acc.addDirectPrintCost(totalMaterialCostRaw);

        BigDecimal unitAmountRaw = context.unitAmount(totalMaterialCostRaw);
        String detailLabel = QuoteConfigReader.formatCompactPtBr(unitWeightSum, 2)
                + " g/un · "
                + QuoteConfigReader.formatCompactPtBr(totalWeightSum, 2)
                + " g total";

        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("filamentCount", filamentBreakdowns.size());
        metadata.put("unitWeightGrams", QuoteConfigReader.scale2(unitWeightSum));
        metadata.put("totalWeightGrams", QuoteConfigReader.scale2(totalWeightSum));

        return new QuotePartResult(
                module.code(),
                module.title(),
                module.quoteType(),
                module.executionOrder(),
                module.enabled(),
                module.overridden(),
                QuoteConfigReader.scale2(unitAmountRaw),
                QuoteConfigReader.scale2(totalMaterialCostRaw),
                totalMaterialCostRaw,
                BigDecimal.ZERO.setScale(2),
                detailLabel,
                Map.copyOf(metadata)
        );
    }

    private String spoolKey(Filament filament) {
        return filament.getId() != null ? "ID:" + filament.getId() : "GUID:" + filament.getGuid();
    }

    private String buildFilamentLabel(ResolvedEntry item) {
        if (item.selectMode) {
            if (item.matched == null) {
                return "Selecione um filamento";
            }
            String materialName = item.matched.getMaterial() != null
                    ? item.matched.getMaterial().getCode()
                    : "";
            return materialName.isBlank()
                    ? item.matched.getName()
                    : item.matched.getName() + " (" + materialName + ")";
        }
        String manualLabel = item.entry.manualLabel();
        return manualLabel != null && !manualLabel.isBlank() ? manualLabel.trim() : "Filamento manual";
    }

    private record ResolvedEntry(
            QuoteInput.FilamentInput entry,
            String mode,
            boolean selectMode,
            Filament matched,
            BigDecimal unitWeight,
            BigDecimal totalWeight,
            BigDecimal effectiveCostPerKg
    ) {
    }
}
