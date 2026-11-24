package org.imprime.ai.api.quote;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.imprime.ai.api.model.Asset;
import org.imprime.ai.api.model.Company;
import org.imprime.ai.api.model.Filament;
import org.imprime.ai.api.model.MakerQuoteSettings;
import org.imprime.ai.api.model.QuoteSettings;
import org.imprime.ai.api.model.enums.QuoteModuleType;
import org.imprime.ai.api.quote.calculator.AdditionalFeeQuotePartCalculator;
import org.imprime.ai.api.util.JsonCodec;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class ModularQuoteEngine {
    private static final BigDecimal NEGATIVE_PROFIT_TOLERANCE = new BigDecimal("-0.005");

    private static final Comparator<ResolvedQuoteModule> EXECUTION_COMPARATOR = Comparator
            .comparingInt(ResolvedQuoteModule::phaseOrder)
            .thenComparingInt(ResolvedQuoteModule::executionOrder)
            .thenComparing(ResolvedQuoteModule::catalogId, Comparator.nullsLast(Long::compareTo))
            .thenComparing(ResolvedQuoteModule::code, Comparator.nullsLast(String::compareToIgnoreCase));

    private final QuotePartCalculatorRegistry calculatorRegistry;
    private final JsonCodec jsonCodec;

    public QuoteCalculationResult calculate(
            QuoteInput input,
            Company company,
            List<QuoteSettings> activeCatalog,
            List<MakerQuoteSettings> makerSettings,
            List<Asset> printers,
            List<Filament> filaments) {
        QuoteInput safeInput = sanitizeInput(input);

        int rawQty = safeInput.quantity() != null ? safeInput.quantity() : 1;
        int quantity = Math.max(1, rawQty);
        String inputScope = normalizeInputScope(safeInput.inputScope());
        Integer unitsPerPlate = "per_plate".equals(inputScope)
                ? Math.max(1, safeInput.unitsPerPlate() != null ? safeInput.unitsPerPlate() : 1)
                : null;
        int estimatedPlates = "per_plate".equals(inputScope)
                ? (quantity + unitsPerPlate - 1) / unitsPerPlate
                : 1;
        String extraCostsScope = "per_unit".equalsIgnoreCase(safeInput.extraCostsScope())
                ? "per_unit"
                : "per_order";

        BigDecimal hoursPart = QuoteConfigReader.sanitizeNonNegative(safeInput.printTimeHours());
        BigDecimal minutesPart = QuoteConfigReader.sanitizeNonNegative(safeInput.printTimeMinutes());
        BigDecimal basePrintHours = hoursPart.add(QuoteConfigReader.divide(minutesPart, QuoteConfigReader.SIXTY));

        BigDecimal qtyDec = BigDecimal.valueOf(quantity);
        BigDecimal unitPrintHours;
        BigDecimal totalPrintHours;
        if ("total_batch".equals(inputScope)) {
            unitPrintHours = QuoteConfigReader.divide(basePrintHours, qtyDec);
            totalPrintHours = basePrintHours;
        } else if ("per_plate".equals(inputScope)) {
            BigDecimal plateCap = BigDecimal.valueOf(unitsPerPlate);
            unitPrintHours = QuoteConfigReader.divide(basePrintHours, plateCap);
            totalPrintHours = QuoteConfigReader.divide(basePrintHours.multiply(qtyDec), plateCap);
        } else {
            unitPrintHours = basePrintHours;
            totalPrintHours = basePrintHours.multiply(qtyDec);
        }

        Map<String, Asset> printersByKey = indexPrinters(printers);
        Map<String, Filament> filamentsByKey = indexFilaments(filaments);

        List<ResolvedQuoteModule> resolvedModules = resolveModules(
                company,
                activeCatalog,
                makerSettings,
                safeInput.moduleOverrides()
        );

        BigDecimal precomputedFeePercent = BigDecimal.ZERO;
        for (ResolvedQuoteModule module : resolvedModules) {
            if (module.enabled() && module.quoteType() == QuoteModuleType.FEE_TAX) {
                precomputedFeePercent = precomputedFeePercent.add(
                        AdditionalFeeQuotePartCalculator.extractFeePercent(module.effectiveConfig())
                );
            }
        }
        BigDecimal precomputedFeeRate = QuoteConfigReader.divide(precomputedFeePercent, QuoteConfigReader.HUNDRED);

        QuoteAccumulator acc = new QuoteAccumulator();
        acc.setAdditionalFeePercent(precomputedFeePercent);

        QuotingContext context = new QuotingContext(
                safeInput,
                company,
                quantity,
                inputScope,
                unitsPerPlate,
                estimatedPlates,
                unitPrintHours,
                totalPrintHours,
                extraCostsScope,
                printersByKey,
                filamentsByKey,
                precomputedFeePercent,
                precomputedFeeRate,
                acc
        );

        // Fase 1: BASE_COST
        executePhase(resolvedModules, QuoteModuleType.BASE_COST, context);

        // Fase 2: COST_MODIFIER
        executePhase(resolvedModules, QuoteModuleType.COST_MODIFIER, context);

        // Fase 3: PROFIT
        executePhase(resolvedModules, QuoteModuleType.PROFIT, context);
        if (!acc.isProfitApplied()) {
            if (precomputedFeePercent.compareTo(QuoteConfigReader.MAX_COMBINED_PERCENT) >= 0) {
                acc.markInvalid();
                acc.addWarningOnce(new QuoteValidationWarning(
                        "INVALID_MARGIN_AND_FEE_SUM",
                        "error",
                        "As taxas adicionais devem ser inferiores a 95%."
                ));
            } else {
                BigDecimal breakEvenBeforeDiscount = QuoteConfigReader.divide(
                        acc.getProductionCostTotal(),
                        BigDecimal.ONE.subtract(precomputedFeeRate)
                );
                acc.setGrossPriceBeforeDiscountTotal(breakEvenBeforeDiscount);
            }
        }

        // Fase 4: DISCOUNT_PROMOTION
        if (acc.isValid()) {
            executePhase(resolvedModules, QuoteModuleType.DISCOUNT_PROMOTION, context);
            BigDecimal netSellingPrice = QuoteConfigReader.sanitizeNonNegative(
                    acc.getGrossPriceBeforeDiscountTotal().subtract(acc.getDiscountTotal())
            );
            acc.setFinalSuggestedPriceTotal(netSellingPrice);
        } else {
            acc.setFinalSuggestedPriceTotal(BigDecimal.ZERO);
        }

        // Fase 5: FEE_TAX (incide sobre o preço líquido final pós-desconto)
        if (acc.isValid()) {
            executePhase(resolvedModules, QuoteModuleType.FEE_TAX, context);
        }

        return consolidate(context, company);
    }

    public List<ResolvedQuoteModule> resolveModules(
            Company company,
            List<QuoteSettings> activeCatalog,
            List<MakerQuoteSettings> makerSettings,
            Map<String, QuoteInput.ModuleOverrideInput> overrides) {
        if (activeCatalog == null || activeCatalog.isEmpty()) {
            return List.of();
        }

        Map<Long, MakerQuoteSettings> makerByCatalogId = new LinkedHashMap<>();
        if (makerSettings != null) {
            for (MakerQuoteSettings row : makerSettings) {
                if (row != null && row.getQuoteSettingsId() != null) {
                    makerByCatalogId.put(row.getQuoteSettingsId(), row);
                }
            }
        }

        Map<String, QuoteInput.ModuleOverrideInput> normalizedOverrides = normalizeOverrides(overrides);

        List<ModuleResolutionCandidate> candidates = new ArrayList<>(activeCatalog.size());
        for (QuoteSettings catalog : activeCatalog) {
            if (catalog == null || catalog.getCode() == null || catalog.getCode().isBlank()) {
                continue;
            }
            String code = catalog.getCode().trim().toUpperCase(Locale.ROOT);
            MakerQuoteSettings makerRow = catalog.getId() != null ? makerByCatalogId.get(catalog.getId()) : null;

            boolean defaultEnabled = !Boolean.FALSE.equals(catalog.getDefaultEnabled());
            boolean canDisable = !Boolean.FALSE.equals(catalog.getCanDisable());
            boolean allowOverride = !Boolean.FALSE.equals(catalog.getAllowQuoteOverride());
            int phaseOrder = catalog.getQuoteType() != null
                    ? catalog.getQuoteType().getPhaseOrder()
                    : (catalog.getPhaseOrder() != null ? catalog.getPhaseOrder() : 0);
            int executionOrder = makerRow != null && makerRow.getExecutionOrder() != null
                    ? makerRow.getExecutionOrder()
                    : (catalog.getExecutionOrder() != null ? catalog.getExecutionOrder() : 0);

            boolean enabled = makerRow != null && makerRow.getEnabled() != null
                    ? makerRow.getEnabled()
                    : defaultEnabled;
            if (!canDisable) {
                enabled = true;
            }

            String makerConfigJson = makerRow != null ? makerRow.getConfigJson() : null;
            ObjectNode effectiveConfig = QuoteConfigReader.mergeConfigs(
                    jsonCodec,
                    catalog.getDefaultConfigJson(),
                    makerConfigJson,
                    null
            );

            syncCompanyRatesIfMissingInMakerRow(company, code, makerRow, makerConfigJson, effectiveConfig);

            QuoteInput.ModuleOverrideInput override = normalizedOverrides.get(code);
            boolean overridden = false;
            boolean explicitlyEnabledByOverride = false;
            boolean explicitlyEnabledByMaker = makerRow != null && Boolean.TRUE.equals(makerRow.getEnabled());

            if (allowOverride && override != null) {
                if (override.enabled() != null) {
                    boolean requestedEnabled = override.enabled();
                    if (canDisable || requestedEnabled) {
                        enabled = requestedEnabled;
                        overridden = true;
                        explicitlyEnabledByOverride = requestedEnabled;
                    }
                }
                if (override.config() != null && hasNonNullOverrideEntries(override.config())) {
                    effectiveConfig = QuoteConfigReader.mergeConfigs(
                            jsonCodec,
                            effectiveConfig.toString(),
                            null,
                            override.config()
                    );
                    overridden = true;
                }
            }

            ResolvedQuoteModule module = new ResolvedQuoteModule(
                    catalog.getId(),
                    code,
                    catalog.getTitle(),
                    catalog.getDescription(),
                    catalog.getQuoteType(),
                    phaseOrder,
                    executionOrder,
                    catalog.getQuoteCalculatorClass(),
                    enabled,
                    canDisable,
                    allowOverride,
                    catalog.getMutualExclusionGroup(),
                    overridden,
                    effectiveConfig
            );
            candidates.add(new ModuleResolutionCandidate(
                    module,
                    explicitlyEnabledByOverride,
                    explicitlyEnabledByMaker
            ));
        }

        candidates.sort(Comparator.comparing(ModuleResolutionCandidate::module, EXECUTION_COMPARATOR));

        Map<String, String> overrideWinnerByGroup = new LinkedHashMap<>();
        Map<String, String> activeWinnerByGroup = new LinkedHashMap<>();
        for (ModuleResolutionCandidate candidate : candidates) {
            ResolvedQuoteModule m = candidate.module();
            String group = normalizeGroup(m.mutualExclusionGroup());
            if (group != null && m.enabled() && candidate.explicitlyEnabledByOverride()) {
                overrideWinnerByGroup.put(group, m.code());
                activeWinnerByGroup.put(group, m.code());
            }
        }
        for (ModuleResolutionCandidate candidate : candidates) {
            ResolvedQuoteModule m = candidate.module();
            String group = normalizeGroup(m.mutualExclusionGroup());
            if (group != null && m.enabled() && candidate.explicitlyEnabledByMaker()) {
                activeWinnerByGroup.putIfAbsent(group, m.code());
            }
        }
        for (ModuleResolutionCandidate candidate : candidates) {
            ResolvedQuoteModule m = candidate.module();
            String group = normalizeGroup(m.mutualExclusionGroup());
            if (group != null && m.enabled()) {
                activeWinnerByGroup.putIfAbsent(group, m.code());
            }
        }

        List<ResolvedQuoteModule> finalModules = new ArrayList<>(candidates.size());
        for (ModuleResolutionCandidate candidate : candidates) {
            ResolvedQuoteModule m = candidate.module();
            String group = normalizeGroup(m.mutualExclusionGroup());
            if (group != null && m.enabled()) {
                String winnerCode = activeWinnerByGroup.get(group);
                if (winnerCode != null && !winnerCode.equals(m.code())) {
                    m = m.withEnabled(false, m.overridden() || overrideWinnerByGroup.containsKey(group));
                }
            }
            finalModules.add(m);
        }

        return List.copyOf(finalModules);
    }

    private void executePhase(
            List<ResolvedQuoteModule> resolvedModules,
            QuoteModuleType phaseType,
            QuotingContext context) {
        for (ResolvedQuoteModule module : resolvedModules) {
            if (!module.enabled() || module.quoteType() != phaseType) {
                continue;
            }
            QuotePartCalculator calculator = calculatorRegistry.resolve(module.calculatorClass());
            QuotePartResult part = calculator.calculate(context, module.effectiveConfig(), module);
            context.getAccumulator().addPart(part);
        }
    }

    private QuoteCalculationResult consolidate(QuotingContext context, Company company) {
        QuoteAccumulator acc = context.getAccumulator();
        BigDecimal qty = BigDecimal.valueOf(context.getQuantity());
        BigDecimal productionCostTotal = acc.getProductionCostTotal();

        BigDecimal suggestedFinalTotal = BigDecimal.ZERO;
        BigDecimal feesCostTotal = BigDecimal.ZERO;
        BigDecimal grossBeforeDiscountTotal = BigDecimal.ZERO;
        BigDecimal discountTotal = BigDecimal.ZERO;
        BigDecimal minimumBreakEvenTotal = BigDecimal.ZERO;
        BigDecimal estimatedNetProfitTotal = BigDecimal.ZERO;
        BigDecimal realProfitMarginOnPricePercent = BigDecimal.ZERO;
        BigDecimal realMarkupOnCostPercent = BigDecimal.ZERO;

        if (acc.isValid()) {
            grossBeforeDiscountTotal = acc.getGrossPriceBeforeDiscountTotal();
            discountTotal = acc.getDiscountTotal();
            suggestedFinalTotal = QuoteConfigReader.sanitizeNonNegative(acc.getFinalSuggestedPriceTotal());
            feesCostTotal = acc.getFeesCostTotal();
            minimumBreakEvenTotal = QuoteConfigReader.divide(
                    productionCostTotal,
                    BigDecimal.ONE.subtract(context.getPrecomputedFeeRate())
            );
            estimatedNetProfitTotal = suggestedFinalTotal
                    .subtract(productionCostTotal)
                    .subtract(feesCostTotal);

            if (suggestedFinalTotal.compareTo(BigDecimal.ZERO) > 0) {
                realProfitMarginOnPricePercent = QuoteConfigReader.divide(estimatedNetProfitTotal, suggestedFinalTotal)
                        .multiply(QuoteConfigReader.HUNDRED);
            }
            if (productionCostTotal.compareTo(BigDecimal.ZERO) > 0) {
                realMarkupOnCostPercent = QuoteConfigReader.divide(estimatedNetProfitTotal, productionCostTotal)
                        .multiply(QuoteConfigReader.HUNDRED);
            }

            if (productionCostTotal.compareTo(BigDecimal.ZERO) > 0
                    && estimatedNetProfitTotal.compareTo(NEGATIVE_PROFIT_TOLERANCE) < 0) {
                acc.addWarningOnce(new QuoteValidationWarning(
                        "BELOW_BREAK_EVEN",
                        "error",
                        "Atenção: o preço final com desconto está abaixo do ponto de equilíbrio (gera prejuízo)."
                ));
            }

            BigDecimal minOrderValue = company != null ? company.getMinOrderValue() : null;
            if (minOrderValue != null
                    && minOrderValue.compareTo(BigDecimal.ZERO) > 0
                    && suggestedFinalTotal.compareTo(BigDecimal.ZERO) > 0
                    && suggestedFinalTotal.compareTo(minOrderValue) < 0) {
                acc.addWarningOnce(new QuoteValidationWarning(
                        "BELOW_MIN_ORDER_VALUE",
                        "warning",
                        "O valor total está abaixo do pedido mínimo configurado no perfil ("
                                + QuoteConfigReader.formatBrl(minOrderValue) + ")."
                ));
            }
        }

        List<QuotePartResult> consolidatedParts = new ArrayList<>(acc.getParts().size());
        for (QuotePartResult part : acc.getParts()) {
            BigDecimal share = BigDecimal.ZERO;
            if ((part.quoteType() == QuoteModuleType.BASE_COST || part.quoteType() == QuoteModuleType.COST_MODIFIER)
                    && productionCostTotal.compareTo(BigDecimal.ZERO) > 0
                    && part.rawTotalAmount() != null
                    && part.rawTotalAmount().compareTo(BigDecimal.ZERO) > 0) {
                share = QuoteConfigReader.divide(part.rawTotalAmount(), productionCostTotal)
                        .multiply(QuoteConfigReader.HUNDRED);
            }
            consolidatedParts.add(part.withShareOfProductionCostPercent(QuoteConfigReader.scale2(share)));
        }

        QuoteCalculationResult.BreakdownAmounts totalAmounts = buildBreakdownAmounts(
                acc.getMaterialCostTotal(),
                acc.getEnergyCostTotal(),
                acc.getDepreciationCostTotal(),
                acc.getDirectPrintCostTotal(),
                acc.getFailureReserveCostTotal(),
                acc.getLaborCostTotal(),
                acc.getPackagingCostTotal(),
                acc.getExtraCostsTotal(),
                productionCostTotal,
                grossBeforeDiscountTotal,
                discountTotal,
                feesCostTotal,
                suggestedFinalTotal,
                minimumBreakEvenTotal,
                estimatedNetProfitTotal
        );

        QuoteCalculationResult.BreakdownAmounts unitAmounts = buildBreakdownAmounts(
                QuoteConfigReader.divide(acc.getMaterialCostTotal(), qty),
                QuoteConfigReader.divide(acc.getEnergyCostTotal(), qty),
                QuoteConfigReader.divide(acc.getDepreciationCostTotal(), qty),
                QuoteConfigReader.divide(acc.getDirectPrintCostTotal(), qty),
                QuoteConfigReader.divide(acc.getFailureReserveCostTotal(), qty),
                QuoteConfigReader.divide(acc.getLaborCostTotal(), qty),
                QuoteConfigReader.divide(acc.getPackagingCostTotal(), qty),
                QuoteConfigReader.divide(acc.getExtraCostsTotal(), qty),
                QuoteConfigReader.divide(productionCostTotal, qty),
                QuoteConfigReader.divide(grossBeforeDiscountTotal, qty),
                QuoteConfigReader.divide(discountTotal, qty),
                QuoteConfigReader.divide(feesCostTotal, qty),
                QuoteConfigReader.divide(suggestedFinalTotal, qty),
                QuoteConfigReader.divide(minimumBreakEvenTotal, qty),
                QuoteConfigReader.divide(estimatedNetProfitTotal, qty)
        );

        QuoteCalculationResult.EffectiveParameters effective = new QuoteCalculationResult.EffectiveParameters(
                context.getQuantity(),
                context.getInputScope(),
                context.getUnitsPerPlate(),
                context.getEstimatedPlates(),
                QuoteConfigReader.scale(context.getUnitPrintHours(), 4),
                QuoteConfigReader.scale(context.getTotalPrintHours(), 4),
                QuoteConfigReader.scale2(acc.getUnitWeightGrams()),
                QuoteConfigReader.scale2(acc.getTotalWeightGrams()),
                QuoteConfigReader.scale2(acc.getPowerWatts()),
                acc.getPowerSource(),
                QuoteConfigReader.scale(acc.getUnitEnergyKwh(), 3),
                QuoteConfigReader.scale(acc.getTotalEnergyKwh(), 3),
                QuoteConfigReader.scale2(acc.getHourlyDepreciationRate()),
                acc.getDepreciationSource(),
                acc.isRequiresLabor(),
                acc.getEffectiveLaborTimeMinutes(),
                QuoteConfigReader.scale2(acc.getFailureRatePercent()),
                QuoteConfigReader.scale2(acc.getProfitMarginPercent()),
                QuoteConfigReader.scale2(acc.getAdditionalFeePercent()),
                QuoteConfigReader.scale2(acc.getDiscountPercent()),
                acc.getActiveProfitStrategy(),
                context.getExtraCostsScope()
        );

        return new QuoteCalculationResult(
                acc.isValid(),
                effective,
                List.copyOf(consolidatedParts),
                new QuoteCalculationResult.BreakdownSummary(
                        totalAmounts,
                        unitAmounts,
                        QuoteConfigReader.scale2(realProfitMarginOnPricePercent),
                        QuoteConfigReader.scale2(realMarkupOnCostPercent)
                ),
                List.copyOf(acc.getFilaments()),
                List.copyOf(acc.getWarnings())
        );
    }

    private QuoteCalculationResult.BreakdownAmounts buildBreakdownAmounts(
            BigDecimal materialCost,
            BigDecimal energyCost,
            BigDecimal depreciationCost,
            BigDecimal directPrintCost,
            BigDecimal failureReserveCost,
            BigDecimal laborCost,
            BigDecimal packagingCost,
            BigDecimal extraCosts,
            BigDecimal productionCost,
            BigDecimal grossBeforeDiscount,
            BigDecimal discountAmount,
            BigDecimal feesCost,
            BigDecimal suggestedFinalPrice,
            BigDecimal minimumBreakEvenPrice,
            BigDecimal estimatedNetProfit) {
        return new QuoteCalculationResult.BreakdownAmounts(
                QuoteConfigReader.scale2(materialCost),
                QuoteConfigReader.scale2(energyCost),
                QuoteConfigReader.scale2(depreciationCost),
                QuoteConfigReader.scale2(directPrintCost),
                QuoteConfigReader.scale2(failureReserveCost),
                QuoteConfigReader.scale2(laborCost),
                QuoteConfigReader.scale2(packagingCost),
                QuoteConfigReader.scale2(extraCosts),
                QuoteConfigReader.scale2(productionCost),
                QuoteConfigReader.scale2(grossBeforeDiscount),
                QuoteConfigReader.scale2(discountAmount),
                QuoteConfigReader.scale2(feesCost),
                QuoteConfigReader.scale2(suggestedFinalPrice),
                QuoteConfigReader.scale2(minimumBreakEvenPrice),
                QuoteConfigReader.scale2(estimatedNetProfit)
        );
    }

    private void syncCompanyRatesIfMissingInMakerRow(
            Company company,
            String code,
            MakerQuoteSettings makerRow,
            String makerConfigJson,
            ObjectNode effectiveConfig) {
        if (company == null) {
            return;
        }
        JsonNode makerNode = makerRow != null ? jsonCodec.readTree(makerConfigJson) : null;
        if ("POWER".equals(code) && company.getKwhRate() != null) {
            if (makerRow == null || !QuoteConfigReader.hasNonNullKey(makerNode, "kwhRate")) {
                effectiveConfig.put("kwhRate", company.getKwhRate());
            }
        }
        if ("DEPRECIATION".equals(code) && company.getHourlyDepreciationRate() != null) {
            if (makerRow == null || !QuoteConfigReader.hasNonNullKey(makerNode, "hourlyDepreciationRate")) {
                effectiveConfig.put("hourlyDepreciationRate", company.getHourlyDepreciationRate());
            }
        }
    }

    private QuoteInput sanitizeInput(QuoteInput input) {
        if (input == null) {
            return new QuoteInput(
                    1,
                    "per_unit",
                    null,
                    BigDecimal.ZERO,
                    BigDecimal.ZERO,
                    new QuoteInput.PrinterInput("select", null, null, null),
                    List.of(),
                    false,
                    BigDecimal.ZERO,
                    null,
                    null,
                    null,
                    "per_order",
                    Map.of()
            );
        }
        return input;
    }

    private String normalizeInputScope(String rawScope) {
        if ("total_batch".equalsIgnoreCase(rawScope)) {
            return "total_batch";
        }
        if ("per_plate".equalsIgnoreCase(rawScope)) {
            return "per_plate";
        }
        return "per_unit";
    }

    private Map<String, QuoteInput.ModuleOverrideInput> normalizeOverrides(
            Map<String, QuoteInput.ModuleOverrideInput> overrides) {
        if (overrides == null || overrides.isEmpty()) {
            return Map.of();
        }
        Map<String, QuoteInput.ModuleOverrideInput> normalized = new LinkedHashMap<>();
        for (Map.Entry<String, QuoteInput.ModuleOverrideInput> entry : overrides.entrySet()) {
            if (entry.getKey() == null || entry.getKey().isBlank() || entry.getValue() == null) {
                continue;
            }
            normalized.put(entry.getKey().trim().toUpperCase(Locale.ROOT), entry.getValue());
        }
        return normalized;
    }

    private boolean hasNonNullOverrideEntries(Map<String, Object> config) {
        for (Map.Entry<String, Object> entry : config.entrySet()) {
            if (entry.getKey() != null && entry.getValue() != null) {
                return true;
            }
        }
        return false;
    }

    private String normalizeGroup(String group) {
        return group != null && !group.isBlank() ? group.trim().toUpperCase(Locale.ROOT) : null;
    }

    private Map<String, Asset> indexPrinters(List<Asset> printers) {
        if (printers == null || printers.isEmpty()) {
            return Map.of();
        }
        Map<String, Asset> map = new LinkedHashMap<>();
        for (Asset printer : printers) {
            if (printer == null) {
                continue;
            }
            if (printer.getGuid() != null && !printer.getGuid().isBlank()) {
                map.putIfAbsent(printer.getGuid().trim(), printer);
            }
            if (printer.getId() != null) {
                map.putIfAbsent(String.valueOf(printer.getId()), printer);
            }
        }
        return map;
    }

    private Map<String, Filament> indexFilaments(List<Filament> filaments) {
        if (filaments == null || filaments.isEmpty()) {
            return Map.of();
        }
        Map<String, Filament> map = new LinkedHashMap<>();
        for (Filament filament : filaments) {
            if (filament == null) {
                continue;
            }
            if (filament.getGuid() != null && !filament.getGuid().isBlank()) {
                map.putIfAbsent(filament.getGuid().trim(), filament);
            }
            if (filament.getId() != null) {
                map.putIfAbsent(String.valueOf(filament.getId()), filament);
            }
        }
        return map;
    }

    private record ModuleResolutionCandidate(
            ResolvedQuoteModule module,
            boolean explicitlyEnabledByOverride,
            boolean explicitlyEnabledByMaker
    ) {
    }
}
