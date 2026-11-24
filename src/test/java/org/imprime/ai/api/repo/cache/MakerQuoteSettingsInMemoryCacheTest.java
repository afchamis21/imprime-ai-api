package org.imprime.ai.api.repo.cache;

import org.imprime.ai.api.http.ServiceContext;
import org.imprime.ai.api.model.MakerQuoteSettings;
import org.imprime.ai.api.model.enums.StatusCd;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MakerQuoteSettingsInMemoryCacheTest {

    private MakerQuoteSettingsInMemoryCache cache;

    @BeforeEach
    void setUp() {
        cache = new MakerQuoteSettingsInMemoryCache();
    }

    @AfterEach
    void tearDown() {
        ServiceContext.clear();
    }

    @Test
    void findByCompanyIdReturnsEmptyOnMissAndNullKey() {
        assertTrue(cache.findByCompanyId(999L).isEmpty());
        assertTrue(cache.findByCompanyId(null).isEmpty());
    }

    @Test
    void putCompanySettingsStoresImmutableDefensiveCopyAndIsolatesCompanies() {
        MakerQuoteSettings company10First = setting(10L, 1L, 10);
        MakerQuoteSettings company10Extra = setting(10L, 2L, 20);
        MakerQuoteSettings company20First = setting(20L, 1L, 10);

        List<MakerQuoteSettings> mutableInput = new ArrayList<>();
        mutableInput.add(company10First);

        cache.putCompanySettings(10L, mutableInput);
        cache.putCompanySettings(20L, List.of(company20First));

        mutableInput.add(company10Extra);

        List<MakerQuoteSettings> cached10 = cache.findByCompanyId(10L).orElseThrow();
        assertEquals(1, cached10.size());
        assertSame(company10First, cached10.get(0));
        assertThrows(UnsupportedOperationException.class, () -> cached10.add(company10Extra));

        List<MakerQuoteSettings> cached20 = cache.findByCompanyId(20L).orElseThrow();
        assertEquals(1, cached20.size());
        assertSame(company20First, cached20.get(0));
    }

    @Test
    void putCompanySettingsSupportsEmptyListAndIgnoresNullInputs() {
        cache.putCompanySettings(10L, List.of());
        assertEquals(List.of(), cache.findByCompanyId(10L).orElseThrow());

        assertDoesNotThrow(() -> cache.putCompanySettings(null, List.of()));
        assertDoesNotThrow(() -> cache.putCompanySettings(10L, null));
        assertEquals(List.of(), cache.findByCompanyId(10L).orElseThrow());
    }

    @Test
    void evictRemovesOnlyTargetCompanyAndIgnoresNull() {
        cache.putCompanySettings(10L, List.of(setting(10L, 1L, 10)));
        cache.putCompanySettings(20L, List.of(setting(20L, 2L, 20)));

        cache.evict(10L);
        assertDoesNotThrow(() -> cache.evict(null));

        assertTrue(cache.findByCompanyId(10L).isEmpty());
        assertTrue(cache.findByCompanyId(20L).isPresent());
    }

    private MakerQuoteSettings setting(Long companyId, Long quoteSettingsId, int executionOrder) {
        MakerQuoteSettings row = new MakerQuoteSettings();
        row.setCompanyId(companyId);
        row.setQuoteSettingsId(quoteSettingsId);
        row.setEnabled(true);
        row.setExecutionOrder(executionOrder);
        row.setConfigJson("{}");
        row.setStatus(StatusCd.ACTIVE);
        return row;
    }
}
