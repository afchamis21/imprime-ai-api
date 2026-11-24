package org.imprime.ai.api.quote;

import org.imprime.ai.api.model.Asset;
import org.imprime.ai.api.model.Company;
import org.imprime.ai.api.model.Filament;
import org.imprime.ai.api.model.MakerQuoteSettings;
import org.imprime.ai.api.model.QuoteSettings;
import org.imprime.ai.api.model.enums.MaterialType;
import org.imprime.ai.api.model.enums.QuoteModuleType;
import org.imprime.ai.api.model.enums.StatusCd;
import org.imprime.ai.api.quote.calculator.AdditionalFeeQuotePartCalculator;
import org.imprime.ai.api.quote.calculator.DepreciationQuotePartCalculator;
import org.imprime.ai.api.quote.calculator.ExtraCostsQuotePartCalculator;
import org.imprime.ai.api.quote.calculator.FailureReserveQuotePartCalculator;
import org.imprime.ai.api.quote.calculator.LaborQuotePartCalculator;
import org.imprime.ai.api.quote.calculator.MaterialQuotePartCalculator;
import org.imprime.ai.api.quote.calculator.PackagingQuotePartCalculator;
import org.imprime.ai.api.quote.calculator.PowerQuotePartCalculator;
import org.imprime.ai.api.quote.calculator.ProfitMarginQuotePartCalculator;
import org.imprime.ai.api.quote.calculator.ProfitMarkupQuotePartCalculator;
import org.imprime.ai.api.quote.calculator.PromotionDiscountQuotePartCalculator;
import org.imprime.ai.api.util.JsonCodec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ModularQuoteEngineTest {

    private ModularQuoteEngine engine;
    private List<QuoteSettings> catalog;
    private Company company;
    private Asset printer;
    private Filament filament;

    @BeforeEach
    void setUp() {
        JsonCodec jsonCodec = new JsonCodec();
        QuotePartCalculatorRegistry registry = new QuotePartCalculatorRegistry(
                List.of(
                        new MaterialQuotePartCalculator(),
                        new PowerQuotePartCalculator(),
                        new DepreciationQuotePartCalculator(),
                        new LaborQuotePartCalculator(),
                        new PackagingQuotePartCalculator(),
                        new ExtraCostsQuotePartCalculator(),
                        new FailureReserveQuotePartCalculator(),
                        new ProfitMarkupQuotePartCalculator(),
                        new ProfitMarginQuotePartCalculator(),
                        new PromotionDiscountQuotePartCalculator(),
                        new AdditionalFeeQuotePartCalculator()
                ),
                null
        );
        engine = new ModularQuoteEngine(registry, jsonCodec);
        catalog = buildV50Catalog();

        company = new Company();
        company.setId(42L);
        company.setKwhRate(new BigDecimal("0.9200"));
        company.setHourlyDepreciationRate(new BigDecimal("1.80"));
        company.setMinOrderValue(BigDecimal.ZERO);

        printer = new Asset();
        printer.setId(10L);
        printer.setGuid("prt-1");
        printer.setPowerWatts(350);

        filament = new Filament();
        filament.setId(20L);
        filament.setGuid("fil-1");
        filament.setName("PLA Matte Branco");
        filament.setMaterial(MaterialType.PLA);
        filament.setCostPerKg(new BigDecimal("135.00"));
        filament.setStock(new BigDecimal("750.00"));
    }

    @Test
    void computesGoldenScenarioAWithLaborMatchingQuoteCalculatorSpec() {
        List<MakerQuoteSettings> makerSettings = goldenSettingsRows();
        QuoteInput input = new QuoteInput(
                2,
                "total_batch",
                new BigDecimal("3"),
                new BigDecimal("30"),
                new QuoteInput.PrinterInput("select", "prt-1", new BigDecimal("300"), null),
                List.of(new QuoteInput.FilamentInput("line-1", "select", "fil-1", "", BigDecimal.ZERO, new BigDecimal("120"))),
                true,
                new BigDecimal("15"),
                "Acabamento",
                new BigDecimal("4.00"),
                "Argolas",
                Map.of()
        );

        QuoteCalculationResult result = engine.calculate(
                input, company, catalog, makerSettings, List.of(printer), List.of(filament));

        assertTrue(result.valid());
        assertEquals(new BigDecimal("3.5000"), result.effective().totalPrintHours());
        assertEquals(new BigDecimal("1.7500"), result.effective().unitPrintHours());
        assertEquals(new BigDecimal("120.00"), result.effective().totalWeightGrams());
        assertEquals(new BigDecimal("60.00"), result.effective().unitWeightGrams());
        assertEquals(new BigDecimal("1.225"), result.effective().totalEnergyKwh());
        assertEquals(new BigDecimal("0.613"), result.effective().unitEnergyKwh());
        assertEquals("PROFIT_MARKUP", result.effective().activeProfitStrategy());

        QuoteCalculationResult.BreakdownAmounts total = result.breakdown().total();
        assertEquals(new BigDecimal("16.20"), total.materialCost());
        assertEquals(new BigDecimal("1.13"), total.energyCost());
        assertEquals(new BigDecimal("6.30"), total.depreciationCost());
        assertEquals(new BigDecimal("23.63"), total.directPrintCost());
        assertEquals(new BigDecimal("2.36"), total.failureReserveCost());
        assertEquals(new BigDecimal("10.00"), total.laborCost());
        assertEquals(new BigDecimal("5.00"), total.packagingCost());
        assertEquals(new BigDecimal("4.00"), total.extraCosts());
        assertEquals(new BigDecimal("44.99"), total.productionCost());
        assertEquals(new BigDecimal("71.04"), total.grossPriceBeforeDiscount());
        assertEquals(new BigDecimal("0.00"), total.discountAmount());
        assertEquals(new BigDecimal("3.55"), total.feesCost());
        assertEquals(new BigDecimal("71.04"), total.suggestedFinalPrice());
        assertEquals(new BigDecimal("47.36"), total.minimumBreakEvenPrice());
        assertEquals(new BigDecimal("22.49"), total.estimatedNetProfit());

        QuoteCalculationResult.BreakdownAmounts unit = result.breakdown().unit();
        assertEquals(new BigDecimal("8.10"), unit.materialCost());
        assertEquals(new BigDecimal("0.56"), unit.energyCost());
        assertEquals(new BigDecimal("3.15"), unit.depreciationCost());
        assertEquals(new BigDecimal("22.49"), unit.productionCost());
        assertEquals(new BigDecimal("35.52"), unit.suggestedFinalPrice());
        assertEquals(new BigDecimal("11.25"), unit.estimatedNetProfit());

        assertEquals(new BigDecimal("31.67"), result.breakdown().realProfitMarginOnPricePercent());
        assertEquals(new BigDecimal("50.00"), result.breakdown().realMarkupOnCostPercent());
        assertEquals(10, result.parts().size());
    }

    @Test
    void zerosLaborCostInGoldenScenarioBWhenRequiresLaborIsFalse() {
        List<MakerQuoteSettings> makerSettings = goldenSettingsRows();
        QuoteInput input = new QuoteInput(
                2,
                "total_batch",
                new BigDecimal("3"),
                new BigDecimal("30"),
                new QuoteInput.PrinterInput("select", "prt-1", new BigDecimal("300"), null),
                List.of(new QuoteInput.FilamentInput("line-1", "select", "fil-1", "", BigDecimal.ZERO, new BigDecimal("120"))),
                false,
                new BigDecimal("15"),
                null,
                new BigDecimal("4.00"),
                null,
                Map.of()
        );

        QuoteCalculationResult result = engine.calculate(
                input, company, catalog, makerSettings, List.of(printer), List.of(filament));

        assertFalse(result.effective().requiresLabor());
        assertEquals(0, result.effective().effectiveLaborTimeMinutes());
        assertEquals(new BigDecimal("0.00"), result.breakdown().total().laborCost());
        assertEquals(new BigDecimal("34.99"), result.breakdown().total().productionCost());
        assertEquals(new BigDecimal("55.25"), result.breakdown().total().suggestedFinalPrice());
        assertEquals(new BigDecimal("27.62"), result.breakdown().unit().suggestedFinalPrice());
        assertEquals(new BigDecimal("17.49"), result.breakdown().total().estimatedNetProfit());
        assertEquals(new BigDecimal("8.75"), result.breakdown().unit().estimatedNetProfit());
    }

    @Test
    void appliesAdditionalFeeAfterPromotionDiscountAndEnforcesProfitStrategyMutualExclusion() {
        List<MakerQuoteSettings> makerSettings = List.of(
                makerRow(1L, true, "{\"defaultCostPerKg\":100.00,\"checkInventoryStock\":true}"),
                makerRow(2L, false, "{\"kwhRate\":1.00,\"defaultPowerWatts\":300}"),
                makerRow(3L, false, "{\"depreciationMode\":\"FIXED_HOURLY\",\"hourlyDepreciationRate\":0.00}"),
                makerRow(4L, true, "{\"laborHourlyRate\":40.00,\"requireExplicitToggleOnQuote\":true}"),
                makerRow(5L, false, "{\"fixedPackagingCost\":0.00}"),
                makerRow(6L, false, "{\"defaultExtraCost\":0.00}"),
                makerRow(7L, false, "{\"failureRatePercent\":0.00}"),
                makerRow(8L, true, "{\"profitMarginPercent\":50.00}"),
                makerRow(9L, false, "{\"profitMarginPercent\":40.00}"),
                makerRow(10L, true, "{\"defaultDiscountPercent\":0.00,\"maxDiscountPercent\":50.00}"),
                makerRow(11L, true, "{\"additionalFeePercent\":10.00,\"feeLabel\":\"Cartão\"}")
        );

        QuoteInput input = new QuoteInput(
                1,
                "total_batch",
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                null,
                List.of(new QuoteInput.FilamentInput("1", "manual", null, "PLA", new BigDecimal("100"), new BigDecimal("500"))),
                false,
                null,
                null,
                null,
                null,
                Map.of(
                        "PROFIT_MARGIN", new QuoteInput.ModuleOverrideInput(true, Map.of("profitMarginPercent", 40)),
                        "PROMOTION_DISCOUNT", new QuoteInput.ModuleOverrideInput(true, Map.of("discountPercent", 10, "campaignLabel", "VIP"))
                )
        );

        QuoteCalculationResult result = engine.calculate(
                input, company, catalog, makerSettings, List.of(), List.of());

        assertTrue(result.valid());
        assertEquals("PROFIT_MARGIN", result.effective().activeProfitStrategy());
        assertEquals(new BigDecimal("50.00"), result.breakdown().total().productionCost());
        // gross = 50 / (1 - 0.40 - 0.10) = 100.00; -10% discount = 90.00; 10% fee on 90.00 = 9.00; net profit = 90 - 50 - 9 = 31.00
        assertEquals(new BigDecimal("100.00"), result.breakdown().total().grossPriceBeforeDiscount());
        assertEquals(new BigDecimal("10.00"), result.breakdown().total().discountAmount());
        assertEquals(new BigDecimal("90.00"), result.breakdown().total().suggestedFinalPrice());
        assertEquals(new BigDecimal("9.00"), result.breakdown().total().feesCost());
        assertEquals(new BigDecimal("31.00"), result.breakdown().total().estimatedNetProfit());
        assertTrue(result.parts().stream().anyMatch(p -> "PROMOTION_DISCOUNT".equals(p.code())
                && new BigDecimal("-10.00").equals(p.totalAmount()) && p.overridden()));
        assertTrue(result.parts().stream().noneMatch(p -> "PROFIT_MARKUP".equals(p.code())));

        QuoteInput invalidMarginSum = new QuoteInput(
                1, "total_batch", BigDecimal.ZERO, BigDecimal.ZERO, null,
                List.of(new QuoteInput.FilamentInput("1", "manual", null, "PLA", new BigDecimal("100"), new BigDecimal("500"))),
                false, null, null, null, null,
                Map.of("PROFIT_MARGIN", new QuoteInput.ModuleOverrideInput(true, Map.of("profitMarginPercent", 85)))
        );
        QuoteCalculationResult invalidRes = engine.calculate(
                invalidMarginSum, company, catalog, makerSettings, List.of(), List.of());
        assertFalse(invalidRes.valid());
        assertEquals(new BigDecimal("0.00"), invalidRes.breakdown().total().suggestedFinalPrice());
        assertTrue(invalidRes.warnings().stream().anyMatch(w -> "INVALID_MARGIN_AND_FEE_SUM".equals(w.code())));
    }

    @Test
    void supportsServiceOnlyQuoteWithAllHardwareAndMaterialModulesDisabled() {
        List<MakerQuoteSettings> serviceOnlySettings = List.of(
                makerRow(1L, false, "{}"),
                makerRow(2L, false, "{}"),
                makerRow(3L, false, "{}"),
                makerRow(4L, true, "{\"laborHourlyRate\":60.00,\"defaultLaborMinutes\":30,\"requireExplicitToggleOnQuote\":true}"),
                makerRow(5L, false, "{}"),
                makerRow(6L, false, "{}"),
                makerRow(7L, false, "{}"),
                makerRow(8L, true, "{\"profitMarginPercent\":50.00}"),
                makerRow(9L, false, "{}"),
                makerRow(10L, false, "{}"),
                makerRow(11L, false, "{}")
        );

        QuoteInput input = new QuoteInput(
                1, "per_unit", BigDecimal.ZERO, BigDecimal.ZERO, null, List.of(),
                true, new BigDecimal("90"), "Modelagem CAD", null, null, Map.of()
        );

        QuoteCalculationResult result = engine.calculate(
                input, company, catalog, serviceOnlySettings, List.of(), List.of());

        assertTrue(result.valid());
        assertEquals(new BigDecimal("90.00"), result.breakdown().total().productionCost());
        assertEquals(new BigDecimal("135.00"), result.breakdown().total().suggestedFinalPrice());
        assertEquals(new BigDecimal("45.00"), result.breakdown().total().estimatedNetProfit());
        assertTrue(result.warnings().isEmpty());
        assertEquals(List.of("LABOR", "PROFIT_MARKUP"),
                result.parts().stream().map(QuotePartResult::code).toList());
    }

    @Test
    void emitsBreakEvenMinOrderWarningsAndComputesBreakEvenWhenProfitDisabled() {
        company.setMinOrderValue(new BigDecimal("30.00"));
        List<MakerQuoteSettings> settings = goldenSettingsRows();

        QuoteInput highDiscountInput = new QuoteInput(
                1,
                "per_unit",
                BigDecimal.ONE,
                BigDecimal.ZERO,
                new QuoteInput.PrinterInput("manual", null, new BigDecimal("300"), new BigDecimal("2.00")),
                List.of(new QuoteInput.FilamentInput("1", "manual", null, "PLA", new BigDecimal("100"), new BigDecimal("100"))),
                false,
                null,
                null,
                null,
                null,
                Map.of("PROMOTION_DISCOUNT", new QuoteInput.ModuleOverrideInput(true, Map.of("discountPercent", 60, "maxDiscountPercent", 100)))
        );

        QuoteCalculationResult lossResult = engine.calculate(
                highDiscountInput, company, catalog, settings, List.of(), List.of());
        assertTrue(lossResult.valid());
        assertTrue(lossResult.breakdown().total().estimatedNetProfit().compareTo(BigDecimal.ZERO) < 0);
        assertTrue(lossResult.warnings().stream().anyMatch(w -> "BELOW_BREAK_EVEN".equals(w.code())));
        assertTrue(lossResult.warnings().stream().anyMatch(w -> "BELOW_MIN_ORDER_VALUE".equals(w.code())));

        QuoteInput noProfitInput = new QuoteInput(
                1,
                "per_unit",
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                null,
                List.of(new QuoteInput.FilamentInput("1", "manual", null, "PLA", new BigDecimal("100"), new BigDecimal("500"))),
                false,
                null,
                null,
                BigDecimal.ZERO,
                null,
                Map.of(
                        "PACKAGING", new QuoteInput.ModuleOverrideInput(false, null),
                        "FAILURE_RESERVE", new QuoteInput.ModuleOverrideInput(false, null),
                        "PROFIT_MARKUP", new QuoteInput.ModuleOverrideInput(false, null),
                        "PROFIT_MARGIN", new QuoteInput.ModuleOverrideInput(false, null),
                        "ADDITIONAL_FEE", new QuoteInput.ModuleOverrideInput(true, Map.of("additionalFeePercent", 20))
                )
        );
        QuoteCalculationResult breakEvenRes = engine.calculate(
                noProfitInput, company, catalog, settings, List.of(), List.of());
        assertTrue(breakEvenRes.valid());
        // cost = 50, fee = 20%, no profit -> gross = 50 / 0.80 = 62.50, fee = 12.50, netProfit = 0.00
        assertEquals(new BigDecimal("62.50"), breakEvenRes.breakdown().total().suggestedFinalPrice());
        assertEquals(new BigDecimal("12.50"), breakEvenRes.breakdown().total().feesCost());
        assertEquals(new BigDecimal("0.00"), breakEvenRes.breakdown().total().estimatedNetProfit());
    }

    @Test
    void calculatesPerPlateBatchWithPerUnitExtraCostsForWholesaleKeychains() {
        List<MakerQuoteSettings> settings = goldenSettingsRows();
        Filament spool = new Filament();
        spool.setId(55L);
        spool.setGuid("fil-keychain");
        spool.setName("PLA Preto");
        spool.setMaterial(MaterialType.PLA);
        spool.setCostPerKg(new BigDecimal("100.00"));
        spool.setStock(new BigDecimal("2000.00"));

        // 500 chaveiros, 24 por mesa (21 mesas estimadas: 20 cheias + 1 parcial de 20)
        // 1 mesa = 3h20m e 180g; argola R$ 0,35 por peça -> total R$ 175,00
        QuoteInput input = new QuoteInput(
                500,
                "per_plate",
                24,
                new BigDecimal("3"),
                new BigDecimal("20"),
                new QuoteInput.PrinterInput("manual", null, new BigDecimal("300"), new BigDecimal("2.00")),
                List.of(new QuoteInput.FilamentInput("1", "select", "fil-keychain", "", BigDecimal.ZERO, new BigDecimal("180"))),
                false,
                null,
                null,
                new BigDecimal("0.35"),
                "Argola de chaveiro",
                "per_unit",
                Map.of("PACKAGING", new QuoteInput.ModuleOverrideInput(false, null))
        );

        QuoteCalculationResult result = engine.calculate(
                input, company, catalog, settings, List.of(), List.of(spool));

        assertTrue(result.valid());
        assertEquals("per_plate", result.effective().inputScope());
        assertEquals(24, result.effective().unitsPerPlate());
        assertEquals(21, result.effective().estimatedPlates());
        assertEquals("per_unit", result.effective().extraCostsScope());
        assertEquals(new BigDecimal("3750.00"), result.effective().totalWeightGrams());
        assertEquals(new BigDecimal("7.50"), result.effective().unitWeightGrams());
        assertEquals(new BigDecimal("69.4444"), result.effective().totalPrintHours());
        assertEquals(new BigDecimal("0.1389"), result.effective().unitPrintHours());
        assertEquals(new BigDecimal("175.00"), result.breakdown().total().extraCosts());
        assertEquals(new BigDecimal("0.35"), result.breakdown().unit().extraCosts());
        assertTrue(result.warnings().stream().anyMatch(w -> "INSUFFICIENT_FILAMENT_STOCK".equals(w.code())));

        // Valida manutenção do escopo per_order quando informado explicitamente
        QuoteInput perOrderInput = new QuoteInput(
                500,
                "per_plate",
                24,
                new BigDecimal("3"),
                new BigDecimal("20"),
                new QuoteInput.PrinterInput("manual", null, new BigDecimal("300"), new BigDecimal("2.00")),
                List.of(new QuoteInput.FilamentInput("1", "select", "fil-keychain", "", BigDecimal.ZERO, new BigDecimal("180"))),
                false,
                null,
                null,
                new BigDecimal("50.00"),
                "Frete coleta",
                "per_order",
                Map.of("PACKAGING", new QuoteInput.ModuleOverrideInput(false, null))
        );
        QuoteCalculationResult perOrderResult = engine.calculate(
                perOrderInput, company, catalog, settings, List.of(), List.of(spool));
        assertEquals("per_order", perOrderResult.effective().extraCostsScope());
        assertEquals(new BigDecimal("50.00"), perOrderResult.breakdown().total().extraCosts());
        assertEquals(new BigDecimal("0.10"), perOrderResult.breakdown().unit().extraCosts());
    }

    private List<MakerQuoteSettings> goldenSettingsRows() {
        return List.of(
                makerRow(1L, true, "{\"defaultCostPerKg\":120.00,\"checkInventoryStock\":true}"),
                makerRow(2L, true, "{\"kwhRate\":0.92,\"defaultPowerWatts\":300}"),
                makerRow(3L, true, "{\"depreciationMode\":\"FIXED_HOURLY\",\"hourlyDepreciationRate\":1.80,\"defaultMachinePrice\":3500.00,\"defaultMachineLifespanHours\":3000,\"defaultMaintenanceHourlyCost\":0.50}"),
                makerRow(4L, true, "{\"laborHourlyRate\":40.00,\"defaultLaborMinutes\":15,\"requireExplicitToggleOnQuote\":true}"),
                makerRow(5L, true, "{\"fixedPackagingCost\":5.00}"),
                makerRow(6L, true, "{\"defaultExtraCost\":0.00}"),
                makerRow(7L, true, "{\"failureRatePercent\":10.00,\"applyOn\":\"DIRECT_PRINT_COST\"}"),
                makerRow(8L, true, "{\"profitMarginPercent\":50.00}"),
                makerRow(9L, false, "{\"profitMarginPercent\":35.00}"),
                makerRow(10L, true, "{\"defaultDiscountPercent\":0.00,\"maxDiscountPercent\":100.00,\"campaignLabel\":\"\"}"),
                makerRow(11L, true, "{\"additionalFeePercent\":5.00,\"feeLabel\":\"Maquininha\"}")
        );
    }

    private MakerQuoteSettings makerRow(Long catalogId, boolean enabled, String configJson) {
        MakerQuoteSettings row = new MakerQuoteSettings();
        row.setCompanyId(42L);
        row.setQuoteSettingsId(catalogId);
        row.setEnabled(enabled);
        row.setConfigJson(configJson);
        row.setStatus(StatusCd.ACTIVE);
        return row;
    }

    private List<QuoteSettings> buildV50Catalog() {
        return List.of(
                catalogEntry(1L, "MATERIAL", "Material", QuoteModuleType.BASE_COST, 10, true, null, MaterialQuotePartCalculator.class),
                catalogEntry(2L, "POWER", "Energia", QuoteModuleType.BASE_COST, 20, true, null, PowerQuotePartCalculator.class),
                catalogEntry(3L, "DEPRECIATION", "Depreciação", QuoteModuleType.BASE_COST, 30, true, null, DepreciationQuotePartCalculator.class),
                catalogEntry(4L, "LABOR", "Mão de Obra", QuoteModuleType.BASE_COST, 40, true, null, LaborQuotePartCalculator.class),
                catalogEntry(5L, "PACKAGING", "Embalagem", QuoteModuleType.BASE_COST, 50, true, null, PackagingQuotePartCalculator.class),
                catalogEntry(6L, "EXTRA_COSTS", "Extras", QuoteModuleType.BASE_COST, 60, true, null, ExtraCostsQuotePartCalculator.class),
                catalogEntry(7L, "FAILURE_RESERVE", "Reserva", QuoteModuleType.COST_MODIFIER, 100, true, null, FailureReserveQuotePartCalculator.class),
                catalogEntry(8L, "PROFIT_MARKUP", "Markup", QuoteModuleType.PROFIT, 200, true, "PROFIT_STRATEGY", ProfitMarkupQuotePartCalculator.class),
                catalogEntry(9L, "PROFIT_MARGIN", "Margem", QuoteModuleType.PROFIT, 210, false, "PROFIT_STRATEGY", ProfitMarginQuotePartCalculator.class),
                catalogEntry(10L, "PROMOTION_DISCOUNT", "Desconto", QuoteModuleType.DISCOUNT_PROMOTION, 300, true, null, PromotionDiscountQuotePartCalculator.class),
                catalogEntry(11L, "ADDITIONAL_FEE", "Taxas", QuoteModuleType.FEE_TAX, 400, true, null, AdditionalFeeQuotePartCalculator.class)
        );
    }

    private QuoteSettings catalogEntry(
            Long id,
            String code,
            String title,
            QuoteModuleType type,
            int executionOrder,
            boolean defaultEnabled,
            String group,
            Class<? extends QuotePartCalculator> calcClass) {
        QuoteSettings qs = new QuoteSettings();
        qs.setId(id);
        qs.setCode(code);
        qs.setTitle(title);
        qs.setDescription(title);
        qs.setQuoteType(type);
        qs.setPhaseOrder(type.getPhaseOrder());
        qs.setExecutionOrder(executionOrder);
        qs.setQuoteCalculatorClass(calcClass.getName());
        qs.setDefaultEnabled(defaultEnabled);
        qs.setCanDisable(true);
        qs.setAllowQuoteOverride(true);
        qs.setMutualExclusionGroup(group);
        qs.setDefaultConfigJson("{}");
        qs.setUiSchemaJson("{}");
        qs.setStatus(StatusCd.ACTIVE);
        return qs;
    }
}
