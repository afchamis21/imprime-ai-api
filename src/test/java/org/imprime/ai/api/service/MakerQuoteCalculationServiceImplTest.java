package org.imprime.ai.api.service;

import org.imprime.ai.api.http.request.maker.CalculateMakerQuoteRequest;
import org.imprime.ai.api.http.request.maker.QuoteModuleOverrideRequest;
import org.imprime.ai.api.http.response.maker.MakerQuoteCalculationResponse;
import org.imprime.ai.api.model.Asset;
import org.imprime.ai.api.model.Company;
import org.imprime.ai.api.model.Filament;
import org.imprime.ai.api.model.MakerQuoteSettings;
import org.imprime.ai.api.model.QuoteSettings;
import org.imprime.ai.api.model.enums.MaterialType;
import org.imprime.ai.api.model.enums.QuoteModuleType;
import org.imprime.ai.api.model.enums.StatusCd;
import org.imprime.ai.api.quote.ModularQuoteEngine;
import org.imprime.ai.api.quote.QuotePartCalculator;
import org.imprime.ai.api.quote.QuotePartCalculatorRegistry;
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
import org.imprime.ai.api.repo.cache.MakerQuoteSettingsInMemoryCache;
import org.imprime.ai.api.repo.cache.QuoteSettingsInMemoryCache;
import org.imprime.ai.api.repo.db.AssetRepository;
import org.imprime.ai.api.repo.db.FilamentRepository;
import org.imprime.ai.api.repo.db.MakerQuoteSettingsRepository;
import org.imprime.ai.api.repo.db.QuoteSettingsRepository;
import org.imprime.ai.api.service.impl.MakerQuoteCalculationServiceImpl;
import org.imprime.ai.api.util.JsonCodec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MakerQuoteCalculationServiceImplTest {

    @Mock
    private MakerCompanyResolver makerCompanyResolver;
    @Mock
    private QuoteSettingsInMemoryCache quoteSettingsInMemoryCache;
    @Mock
    private QuoteSettingsRepository quoteSettingsRepository;
    @Mock
    private MakerQuoteSettingsInMemoryCache makerQuoteSettingsInMemoryCache;
    @Mock
    private MakerQuoteSettingsRepository makerQuoteSettingsRepository;
    @Mock
    private AssetRepository assetRepository;
    @Mock
    private FilamentRepository filamentRepository;

    private MakerQuoteCalculationServiceImpl service;
    private Company company;
    private List<QuoteSettings> catalog;

    @BeforeEach
    void setUp() {
        JsonCodec jsonCodec = new JsonCodec();
        ModularQuoteEngine engine = new ModularQuoteEngine(
                new QuotePartCalculatorRegistry(
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
                        quoteSettingsInMemoryCache
                ),
                jsonCodec
        );
        service = new MakerQuoteCalculationServiceImpl(
                makerCompanyResolver,
                quoteSettingsInMemoryCache,
                quoteSettingsRepository,
                makerQuoteSettingsInMemoryCache,
                makerQuoteSettingsRepository,
                assetRepository,
                filamentRepository,
                engine
        );

        company = new Company();
        company.setId(42L);
        company.setStatus(StatusCd.ACTIVE);
        company.setKwhRate(new BigDecimal("0.9200"));
        company.setHourlyDepreciationRate(new BigDecimal("2.50"));
        company.setMinOrderValue(BigDecimal.ZERO);

        catalog = buildV50Catalog();
        lenient().when(makerCompanyResolver.requireActiveMakerCompany()).thenReturn(company);
        lenient().when(quoteSettingsInMemoryCache.getAllOrdered()).thenReturn(catalog);
    }

    @Test
    void calculateQuotePopulatesMakerCacheOnMissAndLoadsSelectedAssetsAndFilaments() {
        when(makerQuoteSettingsInMemoryCache.findByCompanyId(42L)).thenReturn(Optional.empty());
        when(makerQuoteSettingsRepository.findByCompanyIdAndStatusOrderByExecutionOrderAscIdAsc(42L, StatusCd.ACTIVE))
                .thenReturn(List.of());

        Asset printer = new Asset();
        printer.setId(1L);
        printer.setGuid("prt-1");
        printer.setPowerWatts(300);

        Filament filament = new Filament();
        filament.setId(2L);
        filament.setGuid("fil-1");
        filament.setName("PLA Preto");
        filament.setMaterial(MaterialType.PLA);
        filament.setCostPerKg(new BigDecimal("100.00"));
        filament.setStock(new BigDecimal("500.00"));

        when(assetRepository.findAllByCompanyIdAndStatus(42L, StatusCd.ACTIVE)).thenReturn(List.of(printer));
        when(filamentRepository.findAllByCompanyIdAndStatus(42L, StatusCd.ACTIVE)).thenReturn(List.of(filament));

        CalculateMakerQuoteRequest request = new CalculateMakerQuoteRequest(
                2,
                "per_unit",
                null,
                new BigDecimal("1"),
                BigDecimal.ZERO,
                new CalculateMakerQuoteRequest.PrinterSelectionRequest("select", "prt-1", null, null),
                List.of(new CalculateMakerQuoteRequest.FilamentInputRequest(
                        "f1", "select", "fil-1", null, BigDecimal.ZERO, new BigDecimal("50"))),
                false,
                null,
                null,
                null,
                null,
                "per_order",
                Map.of("PACKAGING", new QuoteModuleOverrideRequest(false, null))
        );

        MakerQuoteCalculationResponse response = service.calculateQuote(request);

        assertTrue(response.isValid());
        assertEquals(new BigDecimal("10.00"), response.breakdown().total().materialCost());
        assertEquals(new BigDecimal("0.00"), response.breakdown().total().packagingCost());
        assertEquals("PROFIT_MARKUP", response.effective().activeProfitStrategy());
        verify(makerQuoteSettingsInMemoryCache).putCompanySettings(42L, List.of());
        verify(assetRepository).findAllByCompanyIdAndStatus(42L, StatusCd.ACTIVE);
        verify(filamentRepository).findAllByCompanyIdAndStatus(42L, StatusCd.ACTIVE);
    }

    @Test
    void calculateQuoteUsesCacheHitAndSkipsInventoryRepositoriesForManualQuote() {
        MakerQuoteSettings cachedPower = new MakerQuoteSettings();
        cachedPower.setCompanyId(42L);
        cachedPower.setQuoteSettingsId(2L);
        cachedPower.setEnabled(false);
        cachedPower.setExecutionOrder(20);
        cachedPower.setConfigJson("{}");
        cachedPower.setStatus(StatusCd.ACTIVE);

        when(makerQuoteSettingsInMemoryCache.findByCompanyId(42L)).thenReturn(Optional.of(List.of(cachedPower)));

        CalculateMakerQuoteRequest manualRequest = new CalculateMakerQuoteRequest(
                1,
                "total_batch",
                null,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                new CalculateMakerQuoteRequest.PrinterSelectionRequest("manual", null, new BigDecimal("300"), null),
                List.of(new CalculateMakerQuoteRequest.FilamentInputRequest(
                        "f1", "manual", null, "PLA Manual", new BigDecimal("100"), new BigDecimal("500"))),
                false,
                null,
                null,
                BigDecimal.ZERO,
                null,
                "per_order",
                Map.of(
                        "DEPRECIATION", new QuoteModuleOverrideRequest(false, null),
                        "PACKAGING", new QuoteModuleOverrideRequest(false, null),
                        "FAILURE_RESERVE", new QuoteModuleOverrideRequest(false, null)
                )
        );

        MakerQuoteCalculationResponse response = service.calculateQuote(manualRequest);

        assertTrue(response.isValid());
        assertEquals(new BigDecimal("50.00"), response.breakdown().total().productionCost());
        assertEquals(new BigDecimal("75.00"), response.breakdown().total().suggestedFinalPrice());

        verifyNoInteractions(makerQuoteSettingsRepository);
        verifyNoInteractions(assetRepository);
        verifyNoInteractions(filamentRepository);
    }

    @Test
    void calculateQuoteSupportsPerPlateAndPerUnitExtraCosts() {
        when(makerQuoteSettingsInMemoryCache.findByCompanyId(42L)).thenReturn(Optional.of(List.of()));

        CalculateMakerQuoteRequest request = new CalculateMakerQuoteRequest(
                500,
                "per_plate",
                24,
                new BigDecimal("3"),
                new BigDecimal("20"),
                new CalculateMakerQuoteRequest.PrinterSelectionRequest("manual", null, new BigDecimal("300"), new BigDecimal("2.00")),
                List.of(new CalculateMakerQuoteRequest.FilamentInputRequest(
                        "f1", "manual", null, "PLA Keychain", new BigDecimal("100"), new BigDecimal("180"))),
                false,
                null,
                null,
                new BigDecimal("0.35"),
                "Argola",
                "per_unit",
                Map.of("PACKAGING", new QuoteModuleOverrideRequest(false, null))
        );

        MakerQuoteCalculationResponse response = service.calculateQuote(request);

        assertTrue(response.isValid());
        assertEquals("per_plate", response.effective().inputScope());
        assertEquals(24, response.effective().unitsPerPlate());
        assertEquals(21, response.effective().estimatedPlates());
        assertEquals("per_unit", response.effective().extraCostsScope());
        assertEquals(new BigDecimal("3750.00"), response.effective().totalWeightGrams());
        assertEquals(new BigDecimal("175.00"), response.breakdown().total().extraCosts());
        assertEquals(new BigDecimal("0.35"), response.breakdown().unit().extraCosts());
    }

    private List<QuoteSettings> buildV50Catalog() {
        return List.of(
                catalogEntry(1L, "MATERIAL", QuoteModuleType.BASE_COST, 10, true, null,
                        MaterialQuotePartCalculator.class, "{\"defaultCostPerKg\":120.00,\"checkInventoryStock\":true}"),
                catalogEntry(2L, "POWER", QuoteModuleType.BASE_COST, 20, true, null,
                        PowerQuotePartCalculator.class, "{\"kwhRate\":0.92,\"defaultPowerWatts\":300}"),
                catalogEntry(3L, "DEPRECIATION", QuoteModuleType.BASE_COST, 30, true, null,
                        DepreciationQuotePartCalculator.class, "{\"depreciationMode\":\"FIXED_HOURLY\",\"hourlyDepreciationRate\":2.50}"),
                catalogEntry(4L, "LABOR", QuoteModuleType.BASE_COST, 40, true, null,
                        LaborQuotePartCalculator.class, "{\"laborHourlyRate\":35.00,\"defaultLaborMinutes\":15,\"requireExplicitToggleOnQuote\":true}"),
                catalogEntry(5L, "PACKAGING", QuoteModuleType.BASE_COST, 50, true, null,
                        PackagingQuotePartCalculator.class, "{\"fixedPackagingCost\":5.00}"),
                catalogEntry(6L, "EXTRA_COSTS", QuoteModuleType.BASE_COST, 60, true, null,
                        ExtraCostsQuotePartCalculator.class, "{\"defaultExtraCost\":0.00}"),
                catalogEntry(7L, "FAILURE_RESERVE", QuoteModuleType.COST_MODIFIER, 100, true, null,
                        FailureReserveQuotePartCalculator.class, "{\"failureRatePercent\":10.00,\"applyOn\":\"DIRECT_PRINT_COST\"}"),
                catalogEntry(8L, "PROFIT_MARKUP", QuoteModuleType.PROFIT, 200, true, "PROFIT_STRATEGY",
                        ProfitMarkupQuotePartCalculator.class, "{\"profitMarginPercent\":50.00}"),
                catalogEntry(9L, "PROFIT_MARGIN", QuoteModuleType.PROFIT, 210, false, "PROFIT_STRATEGY",
                        ProfitMarginQuotePartCalculator.class, "{\"profitMarginPercent\":35.00}"),
                catalogEntry(10L, "PROMOTION_DISCOUNT", QuoteModuleType.DISCOUNT_PROMOTION, 300, true, null,
                        PromotionDiscountQuotePartCalculator.class, "{\"defaultDiscountPercent\":0.00,\"maxDiscountPercent\":50.00}"),
                catalogEntry(11L, "ADDITIONAL_FEE", QuoteModuleType.FEE_TAX, 400, true, null,
                        AdditionalFeeQuotePartCalculator.class, "{\"additionalFeePercent\":0.00,\"feeLabel\":\"Cartão\"}")
        );
    }

    private QuoteSettings catalogEntry(
            Long id,
            String code,
            QuoteModuleType type,
            int executionOrder,
            boolean defaultEnabled,
            String group,
            Class<? extends QuotePartCalculator> calcClass,
            String defaultConfigJson) {
        QuoteSettings qs = new QuoteSettings();
        qs.setId(id);
        qs.setCode(code);
        qs.setTitle(code);
        qs.setDescription(code);
        qs.setQuoteType(type);
        qs.setPhaseOrder(type.getPhaseOrder());
        qs.setExecutionOrder(executionOrder);
        qs.setQuoteCalculatorClass(calcClass.getName());
        qs.setDefaultEnabled(defaultEnabled);
        qs.setCanDisable(true);
        qs.setAllowQuoteOverride(true);
        qs.setMutualExclusionGroup(group);
        qs.setDefaultConfigJson(defaultConfigJson);
        qs.setUiSchemaJson("{}");
        qs.setStatus(StatusCd.ACTIVE);
        return qs;
    }
}
