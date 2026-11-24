package org.imprime.ai.api.quote;

import org.imprime.ai.api.model.QuoteSettings;
import org.imprime.ai.api.model.enums.QuoteModuleType;
import org.imprime.ai.api.model.enums.StatusCd;
import org.imprime.ai.api.model.exception.InternalErrorException;
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
import org.imprime.ai.api.repo.cache.QuoteSettingsInMemoryCache;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class QuotePartCalculatorRegistryTest {

    @Mock
    private QuoteSettingsInMemoryCache quoteSettingsInMemoryCache;

    private QuotePartCalculatorRegistry registry;
    private List<QuoteSettings> v50Catalog;

    @BeforeEach
    void setUp() {
        List<QuotePartCalculator> allCalculators = List.of(
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
        );
        registry = new QuotePartCalculatorRegistry(allCalculators, quoteSettingsInMemoryCache);
        v50Catalog = List.of(
                catalog("MATERIAL", QuoteModuleType.BASE_COST, MaterialQuotePartCalculator.class),
                catalog("POWER", QuoteModuleType.BASE_COST, PowerQuotePartCalculator.class),
                catalog("DEPRECIATION", QuoteModuleType.BASE_COST, DepreciationQuotePartCalculator.class),
                catalog("LABOR", QuoteModuleType.BASE_COST, LaborQuotePartCalculator.class),
                catalog("PACKAGING", QuoteModuleType.BASE_COST, PackagingQuotePartCalculator.class),
                catalog("EXTRA_COSTS", QuoteModuleType.BASE_COST, ExtraCostsQuotePartCalculator.class),
                catalog("FAILURE_RESERVE", QuoteModuleType.COST_MODIFIER, FailureReserveQuotePartCalculator.class),
                catalog("PROFIT_MARKUP", QuoteModuleType.PROFIT, ProfitMarkupQuotePartCalculator.class),
                catalog("PROFIT_MARGIN", QuoteModuleType.PROFIT, ProfitMarginQuotePartCalculator.class),
                catalog("PROMOTION_DISCOUNT", QuoteModuleType.DISCOUNT_PROMOTION, PromotionDiscountQuotePartCalculator.class),
                catalog("ADDITIONAL_FEE", QuoteModuleType.FEE_TAX, AdditionalFeeQuotePartCalculator.class)
        );
    }

    @Test
    void resolvesAndValidatesAllElevenV50CalculatorsOnStartup() {
        when(quoteSettingsInMemoryCache.getAllOrdered()).thenReturn(v50Catalog);

        assertDoesNotThrow(() -> registry.validateCatalogOnStartup());
        for (QuoteSettings module : v50Catalog) {
            QuotePartCalculator resolved = registry.resolve(module.getQuoteCalculatorClass());
            assertNotNull(resolved);
            assertTrue(resolved.supportedType() == module.getQuoteType());
        }
    }

    @Test
    void validateActiveCatalogFailsFastOnUnknownClassOrTypeMismatch() {
        QuoteSettings unknownClass = catalog("CUSTOM", QuoteModuleType.BASE_COST, MaterialQuotePartCalculator.class);
        unknownClass.setQuoteCalculatorClass("com.evil.DynamicEvilCalculator");

        assertThrows(IllegalStateException.class, () -> registry.validateActiveCatalog(List.of(unknownClass)));

        QuoteSettings mismatchedType = catalog("POWER", QuoteModuleType.PROFIT, PowerQuotePartCalculator.class);
        assertThrows(IllegalStateException.class, () -> registry.validateActiveCatalog(List.of(mismatchedType)));
    }

    @Test
    void resolveThrowsInternalErrorExceptionWhenClassNotWhitelisted() {
        assertThrows(InternalErrorException.class,
                () -> registry.resolve("org.imprime.ai.api.quote.calculator.DoesNotExistCalculator"));
        assertThrows(InternalErrorException.class, () -> registry.resolve("  "));
    }

    private QuoteSettings catalog(String code, QuoteModuleType type, Class<? extends QuotePartCalculator> clazz) {
        QuoteSettings qs = new QuoteSettings();
        qs.setCode(code);
        qs.setTitle(code);
        qs.setDescription(code);
        qs.setQuoteType(type);
        qs.setPhaseOrder(type.getPhaseOrder());
        qs.setExecutionOrder(10);
        qs.setQuoteCalculatorClass(clazz.getName());
        qs.setStatus(StatusCd.ACTIVE);
        return qs;
    }
}
