package org.imprime.ai.api.quote.calculator;

import org.imprime.ai.api.model.Asset;
import org.imprime.ai.api.model.Company;
import org.imprime.ai.api.model.Filament;
import org.imprime.ai.api.model.enums.MaterialType;
import org.imprime.ai.api.model.enums.QuoteModuleType;
import org.imprime.ai.api.quote.QuoteAccumulator;
import org.imprime.ai.api.quote.QuoteConfigReader;
import org.imprime.ai.api.quote.QuoteInput;
import org.imprime.ai.api.quote.QuotePartResult;
import org.imprime.ai.api.quote.QuotingContext;
import org.imprime.ai.api.quote.ResolvedQuoteModule;
import org.imprime.ai.api.util.JsonCodec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QuotePartCalculatorsTest {

    private JsonCodec jsonCodec;

    @BeforeEach
    void setUp() {
        jsonCodec = new JsonCodec();
    }

    @Test
    void materialCalculatorAggregatesCombinedSpoolStockAndSupportsDisabledStockCheck() {
        Filament spoolA = new Filament();
        spoolA.setId(1L);
        spoolA.setGuid("fil-a");
        spoolA.setName("PLA Azul");
        spoolA.setMaterial(MaterialType.PLA);
        spoolA.setCostPerKg(new BigDecimal("100.00"));
        spoolA.setStock(new BigDecimal("80.00"));

        QuoteInput input = baseInput(3, "per_unit", BigDecimal.ONE, BigDecimal.ZERO,
                new QuoteInput.PrinterInput("manual", null, new BigDecimal("300"), null),
                List.of(
                        new QuoteInput.FilamentInput("1", "select", "fil-a", null, BigDecimal.ZERO, new BigDecimal("20")),
                        new QuoteInput.FilamentInput("2", "select", "fil-a", null, BigDecimal.ZERO, new BigDecimal("10")),
                        new QuoteInput.FilamentInput("3", "manual", null, "Suporte Solúvel", new BigDecimal("300"), new BigDecimal("10"))
                ),
                false, null, null, null);

        MaterialQuotePartCalculator calculator = new MaterialQuotePartCalculator();

        QuotingContext checkEnabledCtx = context(input, Map.of(), Map.of("fil-a", spoolA), BigDecimal.ZERO);
        QuotePartResult checked = calculator.calculate(
                checkEnabledCtx,
                jsonCodec.readTree("{\"defaultCostPerKg\":120.00,\"checkInventoryStock\":true}"),
                module("MATERIAL", QuoteModuleType.BASE_COST)
        );

        assertEquals(new BigDecimal("18.00"), checked.totalAmount());
        assertEquals(new BigDecimal("6.00"), checked.unitAmount());
        assertTrue(checkEnabledCtx.getAccumulator().getFilaments().get(0).exceedsStock());
        assertTrue(checkEnabledCtx.getAccumulator().getWarnings().stream()
                .anyMatch(w -> "INSUFFICIENT_FILAMENT_STOCK".equals(w.code())));

        QuotingContext checkDisabledCtx = context(input, Map.of(), Map.of("fil-a", spoolA), BigDecimal.ZERO);
        calculator.calculate(
                checkDisabledCtx,
                jsonCodec.readTree("{\"defaultCostPerKg\":120.00,\"checkInventoryStock\":false}"),
                module("MATERIAL", QuoteModuleType.BASE_COST)
        );
        assertFalse(checkDisabledCtx.getAccumulator().getFilaments().get(0).exceedsStock());
        assertTrue(checkDisabledCtx.getAccumulator().getWarnings().isEmpty());
    }

    @Test
    void powerAndDepreciationRespectPrecedenceLevelsAndEmitSingleZeroTimeWarning() {
        Asset printerMissingWatts = new Asset();
        printerMissingWatts.setGuid("prt-zero");
        printerMissingWatts.setPowerWatts(0);

        Asset printerWithRate = new Asset();
        printerWithRate.setGuid("prt-rate");
        printerWithRate.setPowerWatts(400);
        printerWithRate.setPurchasePrice(new BigDecimal("4000.00"));
        printerWithRate.setLifespanHours(2000);
        printerWithRate.setHourlyDepreciationRate(new BigDecimal("2.90"));

        Asset printerLifecycle = new Asset();
        printerLifecycle.setGuid("prt-life");
        printerLifecycle.setPowerWatts(350);
        printerLifecycle.setPurchasePrice(new BigDecimal("4000.00"));
        printerLifecycle.setLifespanHours(2000);
        printerLifecycle.setHourlyDepreciationRate(null);

        Map<String, Asset> printers = Map.of(
                "prt-zero", printerMissingWatts,
                "prt-rate", printerWithRate,
                "prt-life", printerLifecycle
        );

        PowerQuotePartCalculator powerCalc = new PowerQuotePartCalculator();
        DepreciationQuotePartCalculator depCalc = new DepreciationQuotePartCalculator();

        QuoteInput fallbackZeroTime = baseInput(1, "total_batch", BigDecimal.ZERO, BigDecimal.ZERO,
                new QuoteInput.PrinterInput("select", "prt-zero", null, null),
                List.of(), false, null, null, null);
        QuotingContext zeroCtx = context(fallbackZeroTime, printers, Map.of(), BigDecimal.ZERO);
        JsonNode powerCfg = jsonCodec.readTree("{\"kwhRate\":1.00,\"defaultPowerWatts\":300}");
        JsonNode depLifecycleCfg = jsonCodec.readTree(
                "{\"depreciationMode\":\"MACHINE_LIFECYCLE\",\"hourlyDepreciationRate\":2.50,\"defaultMachinePrice\":3000,\"defaultMachineLifespanHours\":1500,\"defaultMaintenanceHourlyCost\":0.50}"
        );

        powerCalc.calculate(zeroCtx, powerCfg, module("POWER", QuoteModuleType.BASE_COST));
        depCalc.calculate(zeroCtx, depLifecycleCfg, module("DEPRECIATION", QuoteModuleType.BASE_COST));

        assertEquals("fallback", zeroCtx.getAccumulator().getPowerSource());
        assertEquals(new BigDecimal("300"), zeroCtx.getAccumulator().getPowerWatts());
        assertEquals("settings_lifecycle", zeroCtx.getAccumulator().getDepreciationSource());
        assertEquals(new BigDecimal("2.50"), QuoteConfigReader.scale2(zeroCtx.getAccumulator().getHourlyDepreciationRate()));
        assertEquals(1, zeroCtx.getAccumulator().getWarnings().stream()
                .filter(w -> "PRINTER_FALLBACK_WATTS".equals(w.code())).count());
        assertEquals(1, zeroCtx.getAccumulator().getWarnings().stream()
                .filter(w -> "ZERO_PRINT_TIME".equals(w.code())).count());

        QuoteInput explicitPrinterRateInput = baseInput(1, "total_batch", new BigDecimal("2"), BigDecimal.ZERO,
                new QuoteInput.PrinterInput("select", "prt-rate", null, null),
                List.of(), false, null, null, null);
        QuotingContext rateCtx = context(explicitPrinterRateInput, printers, Map.of(), BigDecimal.ZERO);
        powerCalc.calculate(rateCtx, powerCfg, module("POWER", QuoteModuleType.BASE_COST));
        QuotePartResult depRateRes = depCalc.calculate(rateCtx, depLifecycleCfg, module("DEPRECIATION", QuoteModuleType.BASE_COST));
        assertEquals("printer", rateCtx.getAccumulator().getPowerSource());
        assertEquals("printer_rate", rateCtx.getAccumulator().getDepreciationSource());
        assertEquals(new BigDecimal("5.80"), depRateRes.totalAmount());

        QuoteInput printerLifecycleInput = baseInput(1, "total_batch", new BigDecimal("2"), BigDecimal.ZERO,
                new QuoteInput.PrinterInput("select", "prt-life", null, null),
                List.of(), false, null, null, null);
        QuotingContext lifeCtx = context(printerLifecycleInput, printers, Map.of(), BigDecimal.ZERO);
        QuotePartResult depLifeRes = depCalc.calculate(lifeCtx, depLifecycleCfg, module("DEPRECIATION", QuoteModuleType.BASE_COST));
        assertEquals("printer_lifecycle", lifeCtx.getAccumulator().getDepreciationSource());
        assertEquals(new BigDecimal("5.00"), depLifeRes.totalAmount());

        QuoteInput manualDepInput = baseInput(1, "total_batch", new BigDecimal("2"), BigDecimal.ZERO,
                new QuoteInput.PrinterInput("manual", null, new BigDecimal("240"), new BigDecimal("3.25")),
                List.of(), false, null, null, null);
        QuotingContext manualCtx = context(manualDepInput, printers, Map.of(), BigDecimal.ZERO);
        powerCalc.calculate(manualCtx, powerCfg, module("POWER", QuoteModuleType.BASE_COST));
        QuotePartResult depManualRes = depCalc.calculate(manualCtx, depLifecycleCfg, module("DEPRECIATION", QuoteModuleType.BASE_COST));
        assertEquals("manual", manualCtx.getAccumulator().getPowerSource());
        assertEquals("manual", manualCtx.getAccumulator().getDepreciationSource());
        assertEquals(new BigDecimal("6.50"), depManualRes.totalAmount());
    }

    @Test
    void laborPackagingExtraCostsAndFailureReserveRespectModesAndApplyOnTargets() {
        LaborQuotePartCalculator laborCalc = new LaborQuotePartCalculator();
        PackagingQuotePartCalculator pkgCalc = new PackagingQuotePartCalculator();
        ExtraCostsQuotePartCalculator extraCalc = new ExtraCostsQuotePartCalculator();
        FailureReserveQuotePartCalculator failureCalc = new FailureReserveQuotePartCalculator();

        QuoteInput noLaborZeroTimeInput = baseInput(2, "total_batch", BigDecimal.ONE, BigDecimal.ZERO,
                new QuoteInput.PrinterInput("manual", null, new BigDecimal("300"), null),
                List.of(), false, BigDecimal.ZERO, "Polimento", null);
        QuotingContext ctx = context(noLaborZeroTimeInput, Map.of(), Map.of(), BigDecimal.ZERO);
        ctx.getAccumulator().addDirectPrintCost(new BigDecimal("20.00"));

        QuotePartResult skippedLabor = laborCalc.calculate(
                ctx,
                jsonCodec.readTree("{\"laborHourlyRate\":40.00,\"defaultLaborMinutes\":15,\"requireExplicitToggleOnQuote\":true}"),
                module("LABOR", QuoteModuleType.BASE_COST)
        );
        assertEquals(new BigDecimal("0.00"), skippedLabor.totalAmount());
        assertEquals("SKIPPED_BY_QUOTE", skippedLabor.metadata().get("status"));

        QuotePartResult forcedLaborZeroWarn = laborCalc.calculate(
                ctx,
                jsonCodec.readTree("{\"laborHourlyRate\":40.00,\"defaultLaborMinutes\":15,\"requireExplicitToggleOnQuote\":false}"),
                module("LABOR", QuoteModuleType.BASE_COST)
        );
        assertEquals(new BigDecimal("0.00"), forcedLaborZeroWarn.totalAmount());
        assertTrue(ctx.getAccumulator().getWarnings().stream()
                .anyMatch(w -> "LABOR_ENABLED_WITH_ZERO_TIME".equals(w.code())));

        QuotePartResult pkgRes = pkgCalc.calculate(
                ctx,
                jsonCodec.readTree("{\"fixedPackagingCost\":5.00}"),
                module("PACKAGING", QuoteModuleType.BASE_COST)
        );
        QuotePartResult extraRes = extraCalc.calculate(
                ctx,
                jsonCodec.readTree("{\"defaultExtraCost\":15.00}"),
                module("EXTRA_COSTS", QuoteModuleType.BASE_COST)
        );
        assertEquals(new BigDecimal("5.00"), pkgRes.totalAmount());
        assertEquals(new BigDecimal("15.00"), extraRes.totalAmount());

        QuotePartResult reserveDirect = failureCalc.calculate(
                ctx,
                jsonCodec.readTree("{\"failureRatePercent\":10.00,\"applyOn\":\"DIRECT_PRINT_COST\"}"),
                module("FAILURE_RESERVE", QuoteModuleType.COST_MODIFIER)
        );
        assertEquals(new BigDecimal("2.00"), reserveDirect.totalAmount());

        QuotePartResult reserveBase = failureCalc.calculate(
                ctx,
                jsonCodec.readTree("{\"failureRatePercent\":10.00,\"applyOn\":\"BASE_PRODUCTION_COST\"}"),
                module("FAILURE_RESERVE", QuoteModuleType.COST_MODIFIER)
        );
        assertEquals(new BigDecimal("4.00"), reserveBase.totalAmount());
    }

    @Test
    void profitDiscountAndAdditionalFeeComputeGrossUpCapAndPostDiscountBasis() {
        ProfitMarkupQuotePartCalculator markupCalc = new ProfitMarkupQuotePartCalculator();
        ProfitMarginQuotePartCalculator marginCalc = new ProfitMarginQuotePartCalculator();
        PromotionDiscountQuotePartCalculator discountCalc = new PromotionDiscountQuotePartCalculator();
        AdditionalFeeQuotePartCalculator feeCalc = new AdditionalFeeQuotePartCalculator();

        QuoteInput input = baseInput(1, "total_batch", BigDecimal.ONE, BigDecimal.ZERO,
                null, List.of(), false, null, null, null);

        // Cost = 50, Margin = 40%, Fee = 10% -> gross = 50 / (1 - 0.40 - 0.10) = 100
        QuotingContext marginCtx = context(input, Map.of(), Map.of(), new BigDecimal("10.00"));
        marginCtx.getAccumulator().addBaseProductionCost(new BigDecimal("50.00"));
        QuotePartResult marginPart = marginCalc.calculate(
                marginCtx,
                jsonCodec.readTree("{\"profitMarginPercent\":40.00}"),
                module("PROFIT_MARGIN", QuoteModuleType.PROFIT)
        );
        assertEquals(new BigDecimal("40.00"), marginPart.totalAmount());
        assertEquals(0, new BigDecimal("100.00").compareTo(
                QuoteConfigReader.scale2(marginCtx.getAccumulator().getGrossPriceBeforeDiscountTotal())));

        // Discount requested 60%, capped at 25% -> -25.00
        QuotePartResult discountPart = discountCalc.calculate(
                marginCtx,
                jsonCodec.readTree("{\"defaultDiscountPercent\":60.00,\"maxDiscountPercent\":25.00,\"campaignLabel\":\"VIP\"}"),
                module("PROMOTION_DISCOUNT", QuoteModuleType.DISCOUNT_PROMOTION)
        );
        assertEquals(new BigDecimal("-25.00"), discountPart.totalAmount());
        assertEquals("-25% (VIP)", discountPart.detailLabel());
        assertTrue(marginCtx.getAccumulator().getWarnings().stream()
                .anyMatch(w -> "DISCOUNT_CAPPED_AT_MAX".equals(w.code())));

        // Net selling price = 100 - 25 = 75 -> 10% fee = 7.50
        marginCtx.getAccumulator().setFinalSuggestedPriceTotal(new BigDecimal("75.00"));
        QuotePartResult feePart = feeCalc.calculate(
                marginCtx,
                jsonCodec.readTree("{\"additionalFeePercent\":10.00,\"feeLabel\":\"Gateway\"}"),
                module("ADDITIONAL_FEE", QuoteModuleType.FEE_TAX)
        );
        assertEquals(new BigDecimal("7.50"), feePart.totalAmount());

        // Margin + fee >= 95% marks accumulator invalid
        QuotingContext invalidMarginCtx = context(input, Map.of(), Map.of(), new BigDecimal("10.00"));
        invalidMarginCtx.getAccumulator().addBaseProductionCost(new BigDecimal("50.00"));
        marginCalc.calculate(
                invalidMarginCtx,
                jsonCodec.readTree("{\"profitMarginPercent\":85.00}"),
                module("PROFIT_MARGIN", QuoteModuleType.PROFIT)
        );
        assertFalse(invalidMarginCtx.getAccumulator().isValid());
        assertTrue(invalidMarginCtx.getAccumulator().getWarnings().stream()
                .anyMatch(w -> "INVALID_MARGIN_AND_FEE_SUM".equals(w.code())));

        // Markup = 50%, fee = 5%, cost = 50 -> gross = (50 * 1.5) / 0.95 = 78.94736842
        QuotingContext markupCtx = context(input, Map.of(), Map.of(), new BigDecimal("5.00"));
        markupCtx.getAccumulator().addBaseProductionCost(new BigDecimal("50.00"));
        QuotePartResult markupPart = markupCalc.calculate(
                markupCtx,
                jsonCodec.readTree("{\"profitMarginPercent\":50.00}"),
                module("PROFIT_MARKUP", QuoteModuleType.PROFIT)
        );
        assertEquals(new BigDecimal("25.00"), markupPart.totalAmount());
        assertEquals(new BigDecimal("78.95"),
                QuoteConfigReader.scale2(markupCtx.getAccumulator().getGrossPriceBeforeDiscountTotal()));
    }

    @Test
    void materialPerPlateAndExtraCostsPerUnitCalculatorsScaleCorrectly() {
        MaterialQuotePartCalculator materialCalc = new MaterialQuotePartCalculator();
        ExtraCostsQuotePartCalculator extraCalc = new ExtraCostsQuotePartCalculator();

        QuoteInput perPlateInput = new QuoteInput(
                500, "per_plate", 24,
                new BigDecimal("3"), new BigDecimal("20"),
                null,
                List.of(new QuoteInput.FilamentInput("1", "manual", null, "PLA", new BigDecimal("100.00"), new BigDecimal("180"))),
                false, null, null,
                new BigDecimal("0.35"), "Argola", "per_unit",
                Map.of()
        );
        QuotingContext perUnitExtraCtx = new QuotingContext(
                perPlateInput, new Company(), 500, "per_plate", 24, 21,
                BigDecimal.ZERO, BigDecimal.ZERO, "per_unit",
                Map.of(), Map.of(), BigDecimal.ZERO, BigDecimal.ZERO, new QuoteAccumulator()
        );

        QuotePartResult matRes = materialCalc.calculate(
                perUnitExtraCtx,
                jsonCodec.readTree("{\"defaultCostPerKg\":100.00,\"checkInventoryStock\":false}"),
                module("MATERIAL", QuoteModuleType.BASE_COST)
        );
        assertEquals(new BigDecimal("0.75"), matRes.unitAmount());
        assertEquals(new BigDecimal("375.00"), matRes.totalAmount());
        assertEquals(new BigDecimal("3750.00"),
                QuoteConfigReader.scale2(perUnitExtraCtx.getAccumulator().getTotalWeightGrams()));

        QuotePartResult perUnitExtraRes = extraCalc.calculate(
                perUnitExtraCtx,
                jsonCodec.readTree("{\"defaultExtraCost\":0.00}"),
                module("EXTRA_COSTS", QuoteModuleType.BASE_COST)
        );
        assertEquals(new BigDecimal("0.35"), perUnitExtraRes.unitAmount());
        assertEquals(new BigDecimal("175.00"), perUnitExtraRes.totalAmount());
        assertEquals("Argola · R$ 0,35/un × 500", perUnitExtraRes.detailLabel());
        assertEquals("per_unit", perUnitExtraRes.metadata().get("scope"));

        QuoteInput perOrderInput = new QuoteInput(
                500, "per_plate", 24,
                BigDecimal.ZERO, BigDecimal.ZERO,
                null, List.of(), false, null, null,
                new BigDecimal("50.00"), "Taxa setup", "per_order", Map.of()
        );
        QuotingContext perOrderExtraCtx = new QuotingContext(
                perOrderInput, new Company(), 500, "per_plate", 24, 21,
                BigDecimal.ZERO, BigDecimal.ZERO, "per_order",
                Map.of(), Map.of(), BigDecimal.ZERO, BigDecimal.ZERO, new QuoteAccumulator()
        );
        QuotePartResult perOrderExtraRes = extraCalc.calculate(
                perOrderExtraCtx,
                jsonCodec.readTree("{\"defaultExtraCost\":0.00}"),
                module("EXTRA_COSTS", QuoteModuleType.BASE_COST)
        );
        assertEquals(new BigDecimal("0.10"), perOrderExtraRes.unitAmount());
        assertEquals(new BigDecimal("50.00"), perOrderExtraRes.totalAmount());
        assertEquals("per_order", perOrderExtraRes.metadata().get("scope"));
    }

    private QuotingContext context(
            QuoteInput input,
            Map<String, Asset> printers,
            Map<String, Filament> filaments,
            BigDecimal precomputedFeePercent) {
        BigDecimal hours = QuoteConfigReader.sanitizeNonNegative(input.printTimeHours())
                .add(QuoteConfigReader.divide(QuoteConfigReader.sanitizeNonNegative(input.printTimeMinutes()), QuoteConfigReader.SIXTY));
        int qty = input.quantity() != null ? Math.max(1, input.quantity()) : 1;
        boolean perUnit = !"total_batch".equalsIgnoreCase(input.inputScope());
        BigDecimal unitHours = perUnit ? hours : QuoteConfigReader.divide(hours, BigDecimal.valueOf(qty));
        BigDecimal totalHours = perUnit ? hours.multiply(BigDecimal.valueOf(qty)) : hours;
        BigDecimal feeRate = QuoteConfigReader.divide(precomputedFeePercent, QuoteConfigReader.HUNDRED);
        Company company = new Company();
        return new QuotingContext(
                input, company, qty, perUnit ? "per_unit" : "total_batch",
                unitHours, totalHours, printers, filaments,
                precomputedFeePercent, feeRate, new QuoteAccumulator()
        );
    }

    private QuoteInput baseInput(
            Integer qty,
            String scope,
            BigDecimal hours,
            BigDecimal minutes,
            QuoteInput.PrinterInput printer,
            List<QuoteInput.FilamentInput> filaments,
            boolean requiresLabor,
            BigDecimal laborMinutes,
            String laborNotes,
            BigDecimal extraCosts) {
        return new QuoteInput(
                qty, scope, hours, minutes, printer, filaments,
                requiresLabor, laborMinutes, laborNotes, extraCosts, null, Map.of()
        );
    }

    private ResolvedQuoteModule module(String code, QuoteModuleType type) {
        return new ResolvedQuoteModule(
                1L, code, code, code, type, type.getPhaseOrder(), 10,
                "calc", true, true, true, null, false, jsonCodec.createObjectNode()
        );
    }
}
