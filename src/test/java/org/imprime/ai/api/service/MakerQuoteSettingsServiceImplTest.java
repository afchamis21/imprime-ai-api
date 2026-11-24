package org.imprime.ai.api.service;

import org.imprime.ai.api.http.request.maker.UpdateMakerQuoteModulesRequest;
import org.imprime.ai.api.http.response.maker.MakerQuoteModuleConfigResponse;
import org.imprime.ai.api.http.response.maker.MakerQuoteSettingsBundleResponse;
import org.imprime.ai.api.model.Company;
import org.imprime.ai.api.model.MakerQuoteSettings;
import org.imprime.ai.api.model.QuoteSettings;
import org.imprime.ai.api.model.enums.QuoteModuleType;
import org.imprime.ai.api.model.enums.StatusCd;
import org.imprime.ai.api.model.exception.BadRequestException;
import org.imprime.ai.api.quote.ModularQuoteEngine;
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
import org.imprime.ai.api.repo.db.CompanyRepository;
import org.imprime.ai.api.repo.db.MakerQuoteSettingsRepository;
import org.imprime.ai.api.repo.db.QuoteSettingsRepository;
import org.imprime.ai.api.service.impl.MakerQuoteSettingsServiceImpl;
import org.imprime.ai.api.util.JsonCodec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MakerQuoteSettingsServiceImplTest {

    @Mock
    private MakerCompanyResolver makerCompanyResolver;
    @Mock
    private CompanyRepository companyRepository;
    @Mock
    private QuoteSettingsRepository quoteSettingsRepository;
    @Mock
    private MakerQuoteSettingsRepository makerQuoteSettingsRepository;
    @Mock
    private QuoteSettingsInMemoryCache quoteSettingsInMemoryCache;
    @Mock
    private MakerQuoteSettingsInMemoryCache makerQuoteSettingsInMemoryCache;

    private JsonCodec jsonCodec;
    private MakerQuoteSettingsServiceImpl service;
    private Company company;
    private List<QuoteSettings> catalog;

    @BeforeEach
    void setUp() {
        jsonCodec = new JsonCodec();
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
        service = new MakerQuoteSettingsServiceImpl(
                makerCompanyResolver,
                companyRepository,
                quoteSettingsRepository,
                makerQuoteSettingsRepository,
                quoteSettingsInMemoryCache,
                makerQuoteSettingsInMemoryCache,
                engine,
                jsonCodec
        );

        company = new Company();
        company.setId(42L);
        company.setStatus(StatusCd.ACTIVE);
        company.setKwhRate(new BigDecimal("1.0800"));
        company.setHourlyDepreciationRate(new BigDecimal("3.20"));
        company.setMinOrderValue(new BigDecimal("25.00"));

        catalog = buildDefaultCatalog();
        lenient().when(makerCompanyResolver.requireActiveMakerCompany()).thenReturn(company);
        lenient().when(quoteSettingsInMemoryCache.getAllOrdered()).thenReturn(catalog);
    }

    @Test
    void getSettingsPopulatesCacheOnMissAndSynthesizesCompanyRates() {
        when(makerQuoteSettingsInMemoryCache.findByCompanyId(42L)).thenReturn(Optional.empty());
        when(makerQuoteSettingsRepository.findByCompanyIdAndStatusOrderByExecutionOrderAscIdAsc(42L, StatusCd.ACTIVE))
                .thenReturn(List.of());

        MakerQuoteSettingsBundleResponse response = service.getSettings();

        assertFalse(response.customized());
        assertNull(response.updatedAt());
        assertEquals(new BigDecimal("1.0800"), response.companyRates().kwhRate());
        assertEquals(new BigDecimal("3.20"), response.companyRates().hourlyDepreciationRate());
        assertEquals(new BigDecimal("25.00"), response.companyRates().minOrderValue());
        assertEquals(catalog.size(), response.modules().size());

        Map<String, MakerQuoteModuleConfigResponse> byCode = response.modules().stream()
                .collect(Collectors.toMap(MakerQuoteModuleConfigResponse::code, Function.identity()));

        assertEquals(0, new BigDecimal("1.0800").compareTo(byCode.get("POWER").config().get("kwhRate").decimalValue()));
        assertEquals(0, new BigDecimal("3.20").compareTo(byCode.get("DEPRECIATION").config().get("hourlyDepreciationRate").decimalValue()));
        assertTrue(byCode.get("PROFIT_MARKUP").enabled());
        assertFalse(byCode.get("PROFIT_MARGIN").enabled());
        assertEquals("energy", byCode.get("POWER").uiSchema().get("group").asString());

        verify(makerQuoteSettingsInMemoryCache).putCompanySettings(42L, List.of());
    }

    @Test
    void getSettingsUsesCacheHitWithoutHittingDatabase() {
        Instant updatedAt = Instant.parse("2026-11-23T18:00:00Z");
        MakerQuoteSettings marginRow = moduleRow(catalog.get(8), true, "{\"profitMarginPercent\":32.00}", updatedAt);
        when(makerQuoteSettingsInMemoryCache.findByCompanyId(42L)).thenReturn(Optional.of(List.of(marginRow)));

        MakerQuoteSettingsBundleResponse response = service.getSettings();

        assertTrue(response.customized());
        assertEquals(updatedAt, response.updatedAt());
        Map<String, MakerQuoteModuleConfigResponse> byCode = response.modules().stream()
                .collect(Collectors.toMap(MakerQuoteModuleConfigResponse::code, Function.identity()));
        assertTrue(byCode.get("PROFIT_MARGIN").enabled());
        assertFalse(byCode.get("PROFIT_MARKUP").enabled());
        assertEquals(0, new BigDecimal("32.00")
                .compareTo(byCode.get("PROFIT_MARGIN").config().get("profitMarginPercent").decimalValue()));

        verifyNoInteractions(makerQuoteSettingsRepository);
    }

    @Test
    @SuppressWarnings("unchecked")
    void updateSettingsPersistsModulesSyncsCompanyAndEvictsCacheAfterCommit() {
        when(makerQuoteSettingsRepository.findByCompanyIdAndStatusOrderByExecutionOrderAscIdAsc(42L, StatusCd.ACTIVE))
                .thenReturn(List.of());
        when(companyRepository.save(any(Company.class))).thenAnswer(inv -> inv.getArgument(0));
        when(makerQuoteSettingsRepository.saveAll(anyList())).thenAnswer(inv -> {
            List<MakerQuoteSettings> items = inv.getArgument(0);
            Instant now = Instant.now();
            for (MakerQuoteSettings item : items) {
                item.setCreateDt(now);
            }
            return items;
        });

        UpdateMakerQuoteModulesRequest request = new UpdateMakerQuoteModulesRequest(List.of(
                new UpdateMakerQuoteModulesRequest.MakerQuoteModuleUpdateItem(
                        "power", true, 20, Map.of("kwhRate", new BigDecimal("0.9850"), "defaultPowerWatts", 320)),
                new UpdateMakerQuoteModulesRequest.MakerQuoteModuleUpdateItem(
                        "DEPRECIATION", true, 30, Map.of("hourlyDepreciationRate", new BigDecimal("2.80"))),
                new UpdateMakerQuoteModulesRequest.MakerQuoteModuleUpdateItem(
                        "PACKAGING", false, 50, Map.of("fixedPackagingCost", new BigDecimal("5.00"))),
                new UpdateMakerQuoteModulesRequest.MakerQuoteModuleUpdateItem(
                        "PROFIT_MARKUP", false, 200, Map.of("profitMarginPercent", new BigDecimal("50.00"))),
                new UpdateMakerQuoteModulesRequest.MakerQuoteModuleUpdateItem(
                        "PROFIT_MARGIN", true, 210, Map.of("profitMarginPercent", new BigDecimal("38.00"))),
                new UpdateMakerQuoteModulesRequest.MakerQuoteModuleUpdateItem(
                        "ADDITIONAL_FEE", true, 400, Map.of("additionalFeePercent", new BigDecimal("4.50")))
        ));

        MakerQuoteSettingsBundleResponse response = service.updateSettings(request);

        assertEquals(new BigDecimal("0.9850"), company.getKwhRate());
        assertEquals(new BigDecimal("2.80"), company.getHourlyDepreciationRate());
        assertEquals(new BigDecimal("0.9850"), response.companyRates().kwhRate());
        assertTrue(response.customized());
        assertNotNull(response.updatedAt());

        ArgumentCaptor<List<MakerQuoteSettings>> captor = ArgumentCaptor.forClass(List.class);
        verify(makerQuoteSettingsRepository).saveAll(captor.capture());
        assertEquals(catalog.size(), captor.getValue().size());
        verify(makerQuoteSettingsInMemoryCache).evictAfterCommit(42L);

        Map<String, MakerQuoteModuleConfigResponse> byCode = response.modules().stream()
                .collect(Collectors.toMap(MakerQuoteModuleConfigResponse::code, Function.identity()));
        assertFalse(byCode.get("PACKAGING").enabled());
        assertFalse(byCode.get("PROFIT_MARKUP").enabled());
        assertTrue(byCode.get("PROFIT_MARGIN").enabled());
        assertEquals(320, byCode.get("POWER").config().get("defaultPowerWatts").asInt());
    }

    @Test
    void updateSettingsRejectsMutualExclusionUnknownDuplicateAndNonDisableableModules() {
        catalog.getFirst().setCanDisable(false);

        // 1. Disabling non-disableable module
        UpdateMakerQuoteModulesRequest disableMaterial = new UpdateMakerQuoteModulesRequest(List.of(
                new UpdateMakerQuoteModulesRequest.MakerQuoteModuleUpdateItem("MATERIAL", false, 10, Map.of())
        ));
        assertThrows(BadRequestException.class, () -> service.updateSettings(disableMaterial));

        // 2. Enabling both PROFIT_MARKUP and PROFIT_MARGIN simultaneously
        UpdateMakerQuoteModulesRequest bothProfits = new UpdateMakerQuoteModulesRequest(List.of(
                new UpdateMakerQuoteModulesRequest.MakerQuoteModuleUpdateItem("PROFIT_MARKUP", true, 200, Map.of()),
                new UpdateMakerQuoteModulesRequest.MakerQuoteModuleUpdateItem("PROFIT_MARGIN", true, 210, Map.of())
        ));
        assertThrows(BadRequestException.class, () -> service.updateSettings(bothProfits));

        // 3. Enabling PROFIT_MARGIN in partial update while PROFIT_MARKUP defaults to true
        UpdateMakerQuoteModulesRequest partialMarginConflict = new UpdateMakerQuoteModulesRequest(List.of(
                new UpdateMakerQuoteModulesRequest.MakerQuoteModuleUpdateItem("PROFIT_MARGIN", true, 210, Map.of())
        ));
        assertThrows(BadRequestException.class, () -> service.updateSettings(partialMarginConflict));

        // 4. Duplicate code and unknown code
        UpdateMakerQuoteModulesRequest duplicateCode = new UpdateMakerQuoteModulesRequest(List.of(
                new UpdateMakerQuoteModulesRequest.MakerQuoteModuleUpdateItem("POWER", true, 20, Map.of()),
                new UpdateMakerQuoteModulesRequest.MakerQuoteModuleUpdateItem("power", true, 20, Map.of())
        ));
        assertThrows(BadRequestException.class, () -> service.updateSettings(duplicateCode));

        UpdateMakerQuoteModulesRequest unknownCode = new UpdateMakerQuoteModulesRequest(List.of(
                new UpdateMakerQuoteModulesRequest.MakerQuoteModuleUpdateItem("NON_EXISTENT", true, 10, Map.of())
        ));
        assertThrows(BadRequestException.class, () -> service.updateSettings(unknownCode));

        // 5. Combined margin + fee >= 95%
        UpdateMakerQuoteModulesRequest invalidSum = new UpdateMakerQuoteModulesRequest(List.of(
                new UpdateMakerQuoteModulesRequest.MakerQuoteModuleUpdateItem("PROFIT_MARKUP", false, 200, Map.of()),
                new UpdateMakerQuoteModulesRequest.MakerQuoteModuleUpdateItem("PROFIT_MARGIN", true, 210, Map.of("profitMarginPercent", 85)),
                new UpdateMakerQuoteModulesRequest.MakerQuoteModuleUpdateItem("ADDITIONAL_FEE", true, 400, Map.of("additionalFeePercent", 10))
        ));
        assertThrows(BadRequestException.class, () -> service.updateSettings(invalidSum));

        verify(companyRepository, never()).save(any());
        verify(makerQuoteSettingsRepository, never()).saveAll(anyList());
        verify(makerQuoteSettingsInMemoryCache, never()).evictAfterCommit(eq(42L));
    }

    private MakerQuoteSettings moduleRow(QuoteSettings catalogEntry, boolean enabled, String configJson, Instant updatedAt) {
        MakerQuoteSettings row = new MakerQuoteSettings();
        row.setCompanyId(42L);
        row.setQuoteSettingsId(catalogEntry.getId());
        row.setEnabled(enabled);
        row.setExecutionOrder(catalogEntry.getExecutionOrder());
        row.setConfigJson(configJson);
        row.setStatus(StatusCd.ACTIVE);
        row.setUpdateDt(updatedAt);
        return row;
    }

    private List<QuoteSettings> buildDefaultCatalog() {
        return List.of(
                catalogEntry(1L, "MATERIAL", QuoteModuleType.BASE_COST, 10, 10, true, null,
                        "{\"defaultCostPerKg\":120.00,\"checkInventoryStock\":true}", "{\"group\":\"print\"}"),
                catalogEntry(2L, "POWER", QuoteModuleType.BASE_COST, 10, 20, true, null,
                        "{\"kwhRate\":0.92,\"defaultPowerWatts\":300}", "{\"group\":\"energy\",\"syncFinanceField\":\"kwhRate\"}"),
                catalogEntry(3L, "DEPRECIATION", QuoteModuleType.BASE_COST, 10, 30, true, null,
                        "{\"depreciationMode\":\"FIXED_HOURLY\",\"hourlyDepreciationRate\":2.50,\"defaultMachinePrice\":3500.00,\"defaultMachineLifespanHours\":3000,\"defaultMaintenanceHourlyCost\":0.50}",
                        "{\"group\":\"energy\",\"syncFinanceField\":\"hourlyDepreciationRate\"}"),
                catalogEntry(4L, "LABOR", QuoteModuleType.BASE_COST, 10, 40, true, null,
                        "{\"laborHourlyRate\":35.00,\"defaultLaborMinutes\":15,\"requireExplicitToggleOnQuote\":true}", "{\"group\":\"operation\"}"),
                catalogEntry(5L, "PACKAGING", QuoteModuleType.BASE_COST, 10, 50, true, null,
                        "{\"fixedPackagingCost\":5.00}", "{\"group\":\"operation\"}"),
                catalogEntry(6L, "EXTRA_COSTS", QuoteModuleType.BASE_COST, 10, 60, true, null,
                        "{\"defaultExtraCost\":0.00}", "{\"group\":\"operation\"}"),
                catalogEntry(7L, "FAILURE_RESERVE", QuoteModuleType.COST_MODIFIER, 20, 100, true, null,
                        "{\"failureRatePercent\":10.00,\"applyOn\":\"DIRECT_PRINT_COST\"}", "{\"group\":\"operation\"}"),
                catalogEntry(8L, "PROFIT_MARKUP", QuoteModuleType.PROFIT, 30, 200, true, "PROFIT_STRATEGY",
                        "{\"profitMarginPercent\":50.00}", "{\"group\":\"pricing\"}"),
                catalogEntry(9L, "PROFIT_MARGIN", QuoteModuleType.PROFIT, 30, 210, false, "PROFIT_STRATEGY",
                        "{\"profitMarginPercent\":35.00}", "{\"group\":\"pricing\"}"),
                catalogEntry(10L, "PROMOTION_DISCOUNT", QuoteModuleType.DISCOUNT_PROMOTION, 40, 300, true, null,
                        "{\"defaultDiscountPercent\":0.00,\"maxDiscountPercent\":50.00,\"campaignLabel\":\"\"}", "{\"group\":\"pricing\"}"),
                catalogEntry(11L, "ADDITIONAL_FEE", QuoteModuleType.FEE_TAX, 50, 400, true, null,
                        "{\"additionalFeePercent\":0.00,\"feeLabel\":\"Maquininha / Marketplace\"}", "{\"group\":\"pricing\"}")
        );
    }

    private QuoteSettings catalogEntry(
            Long id,
            String code,
            QuoteModuleType type,
            int phaseOrder,
            int executionOrder,
            boolean defaultEnabled,
            String mutualExclusionGroup,
            String defaultConfigJson,
            String uiSchemaJson) {
        QuoteSettings qs = new QuoteSettings();
        qs.setId(id);
        qs.setCode(code);
        qs.setTitle(code);
        qs.setDescription(code);
        qs.setQuoteType(type);
        qs.setPhaseOrder(phaseOrder);
        qs.setExecutionOrder(executionOrder);
        qs.setQuoteCalculatorClass("org.imprime.ai.api.quote.calculator." + code);
        qs.setDefaultEnabled(defaultEnabled);
        qs.setCanDisable(true);
        qs.setAllowQuoteOverride(true);
        qs.setMutualExclusionGroup(mutualExclusionGroup);
        qs.setDefaultConfigJson(defaultConfigJson);
        qs.setUiSchemaJson(uiSchemaJson);
        qs.setStatus(StatusCd.ACTIVE);
        return qs;
    }
}
