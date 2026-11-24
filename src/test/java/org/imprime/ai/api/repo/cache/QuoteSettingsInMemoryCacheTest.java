package org.imprime.ai.api.repo.cache;

import org.imprime.ai.api.http.ServiceContext;
import org.imprime.ai.api.model.QuoteSettings;
import org.imprime.ai.api.model.User;
import org.imprime.ai.api.model.enums.QuoteModuleType;
import org.imprime.ai.api.model.enums.StatusCd;
import org.imprime.ai.api.repo.db.QuoteSettingsRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class QuoteSettingsInMemoryCacheTest {

    @Mock
    private QuoteSettingsRepository quoteSettingsRepository;

    private QuoteSettingsInMemoryCache cache;

    @BeforeEach
    void setUp() {
        cache = new QuoteSettingsInMemoryCache(quoteSettingsRepository);
    }

    @AfterEach
    void tearDown() {
        ServiceContext.clear();
    }

    @Test
    void initLoadsCatalogOrderedDeterministicallyAndServesHitsFromMemory() {
        QuoteSettings additionalFee = module(11L, "ADDITIONAL_FEE", QuoteModuleType.FEE_TAX, 50, 400);
        QuoteSettings power = module(2L, "POWER", QuoteModuleType.BASE_COST, 10, 20);
        QuoteSettings material = module(1L, "MATERIAL", QuoteModuleType.BASE_COST, 10, 10);

        when(quoteSettingsRepository.findByStatusOrderByPhaseOrderAscExecutionOrderAscIdAsc(StatusCd.ACTIVE))
                .thenReturn(List.of(additionalFee, power, material));

        cache.init();

        List<QuoteSettings> ordered = cache.getAllOrdered();
        assertEquals(List.of("MATERIAL", "POWER", "ADDITIONAL_FEE"),
                ordered.stream().map(QuoteSettings::getCode).toList());
        assertThrows(UnsupportedOperationException.class, () -> ordered.add(material));

        assertSame(power, cache.findByCode(" power ").orElseThrow());
        assertSame(power, cache.findById(2L).orElseThrow());
        assertTrue(cache.findByCode("UNKNOWN").isEmpty());
        assertTrue(cache.findByCode(null).isEmpty());
        assertTrue(cache.findByCode("  ").isEmpty());
        assertTrue(cache.findById(null).isEmpty());

        verify(quoteSettingsRepository, times(1))
                .findByStatusOrderByPhaseOrderAscExecutionOrderAscIdAsc(StatusCd.ACTIVE);
    }

    @Test
    void reloadCatalogRefreshesEntriesEvictsRemovedCodesAndPreservesServiceContext() {
        User caller = new User();
        caller.setId(99L);
        caller.setGuid("user-guid-99");
        ServiceContext.getContext().setUser(caller);

        QuoteSettings material = module(1L, "MATERIAL", QuoteModuleType.BASE_COST, 10, 10);
        QuoteSettings power = module(2L, "POWER", QuoteModuleType.BASE_COST, 10, 20);
        QuoteSettings labor = module(4L, "LABOR", QuoteModuleType.BASE_COST, 10, 40);

        when(quoteSettingsRepository.findByStatusOrderByPhaseOrderAscExecutionOrderAscIdAsc(StatusCd.ACTIVE))
                .thenReturn(List.of(material, power))
                .thenReturn(List.of(material, labor));

        cache.reloadCatalog();
        assertTrue(cache.findByCode("POWER").isPresent());
        assertTrue(cache.findByCode("LABOR").isEmpty());

        cache.reloadCatalog();
        assertTrue(cache.findByCode("POWER").isEmpty());
        assertTrue(cache.findByCode("LABOR").isPresent());
        assertEquals(List.of("MATERIAL", "LABOR"),
                cache.getAllOrdered().stream().map(QuoteSettings::getCode).toList());
        assertSame(caller, ServiceContext.getUser().orElse(null));
    }

    @Test
    void initHandlesEmptyCatalogAndPreservesPreviousSnapshotOnScheduledReloadFailure() {
        when(quoteSettingsRepository.findByStatusOrderByPhaseOrderAscExecutionOrderAscIdAsc(StatusCd.ACTIVE))
                .thenReturn(List.of());

        cache.init();
        assertTrue(cache.getAllOrdered().isEmpty());

        QuoteSettings material = module(1L, "MATERIAL", QuoteModuleType.BASE_COST, 10, 10);
        when(quoteSettingsRepository.findByStatusOrderByPhaseOrderAscExecutionOrderAscIdAsc(StatusCd.ACTIVE))
                .thenReturn(List.of(material))
                .thenThrow(new RuntimeException("DB down"));

        cache.reloadCatalog();
        assertEquals(1, cache.getAllOrdered().size());

        cache.init();
        assertEquals(List.of("MATERIAL"),
                cache.getAllOrdered().stream().map(QuoteSettings::getCode).toList());
        assertSame(material, cache.findByCode("MATERIAL").orElseThrow());
    }

    @Test
    void reloadCatalogThrowsOnDuplicateNormalizedModuleCodes() {
        QuoteSettings first = module(1L, "power", QuoteModuleType.BASE_COST, 10, 20);
        QuoteSettings second = module(2L, "POWER", QuoteModuleType.BASE_COST, 10, 30);

        when(quoteSettingsRepository.findByStatusOrderByPhaseOrderAscExecutionOrderAscIdAsc(StatusCd.ACTIVE))
                .thenReturn(List.of(first, second));

        assertThrows(IllegalStateException.class, () -> cache.reloadCatalog());
    }

    private QuoteSettings module(Long id, String code, QuoteModuleType type, int phaseOrder, int executionOrder) {
        QuoteSettings qs = new QuoteSettings();
        qs.setId(id);
        qs.setCode(code);
        qs.setTitle(code);
        qs.setDescription(code);
        qs.setQuoteType(type);
        qs.setPhaseOrder(phaseOrder);
        qs.setExecutionOrder(executionOrder);
        qs.setQuoteCalculatorClass("org.imprime.ai.api.quote.calculator." + code);
        qs.setDefaultEnabled(true);
        qs.setCanDisable(true);
        qs.setAllowQuoteOverride(true);
        qs.setDefaultConfigJson("{}");
        qs.setUiSchemaJson("{}");
        qs.setStatus(StatusCd.ACTIVE);
        return qs;
    }
}
