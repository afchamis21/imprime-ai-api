package org.imprime.ai.api.repo.cache;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.imprime.ai.api.model.QuoteSettings;
import org.imprime.ai.api.model.enums.StatusCd;
import org.imprime.ai.api.repo.cache.base.Cached;
import org.imprime.ai.api.repo.cache.base.StaticInMemoryCache;
import org.imprime.ai.api.repo.db.QuoteSettingsRepository;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Repository;

import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Slf4j
@Repository
@RequiredArgsConstructor
public class QuoteSettingsInMemoryCache extends StaticInMemoryCache<String, QuoteSettings> {

    private record CatalogSnapshot(List<QuoteSettings> ordered, Map<Long, QuoteSettings> byId) {
        static final CatalogSnapshot EMPTY = new CatalogSnapshot(List.of(), Map.of());
    }

    private static final Comparator<QuoteSettings> CATALOG_ORDER = Comparator
            .comparing(QuoteSettings::getPhaseOrder, Comparator.nullsLast(Integer::compareTo))
            .thenComparing(QuoteSettings::getExecutionOrder, Comparator.nullsLast(Integer::compareTo))
            .thenComparing(QuoteSettings::getId, Comparator.nullsLast(Long::compareTo))
            .thenComparing(QuoteSettings::getCode, Comparator.nullsLast(String::compareToIgnoreCase));

    private final QuoteSettingsRepository quoteSettingsRepository;

    private volatile CatalogSnapshot snapshot = CatalogSnapshot.EMPTY;

    @PostConstruct
    public void init() {
        super.reload();
    }

    public void reloadCatalog() {
        super.load();
    }

    public List<QuoteSettings> getAllOrdered() {
        return snapshot.ordered();
    }

    public Optional<QuoteSettings> findByCode(String code) {
        String normalized = normalizeCode(code);
        if (normalized == null) {
            return Optional.empty();
        }
        return super.get(normalized).map(Cached::value);
    }

    public Optional<QuoteSettings> findById(Long id) {
        if (id == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(snapshot.byId().get(id));
    }

    @Override
    protected @Nullable String getKey(@Nullable QuoteSettings value) {
        return value == null ? null : normalizeCode(value.getCode());
    }

    @Override
    protected @Nullable List<QuoteSettings> loadAll() {
        List<QuoteSettings> activeCatalog =
                quoteSettingsRepository.findByStatusOrderByPhaseOrderAscExecutionOrderAscIdAsc(StatusCd.ACTIVE);
        if (activeCatalog == null || activeCatalog.isEmpty()) {
            log.warn("No active quote settings found on database!");
            this.snapshot = CatalogSnapshot.EMPTY;
            return List.of();
        }

        List<QuoteSettings> sortedValid = activeCatalog.stream()
                .filter(item -> item != null && normalizeCode(item.getCode()) != null)
                .sorted(CATALOG_ORDER)
                .toList();

        Set<String> seenCodes = new HashSet<>(sortedValid.size());
        Map<Long, QuoteSettings> idMap = new HashMap<>(sortedValid.size());

        for (QuoteSettings item : sortedValid) {
            String normalizedCode = normalizeCode(item.getCode());
            if (!seenCodes.add(normalizedCode)) {
                throw new IllegalStateException("Duplicate QuoteSettings code found! [%s]".formatted(normalizedCode));
            }
            if (item.getId() != null) {
                idMap.putIfAbsent(item.getId(), item);
            }
        }

        List<QuoteSettings> ordered = List.copyOf(sortedValid);
        Map<Long, QuoteSettings> byId = Map.copyOf(idMap);
        this.snapshot = new CatalogSnapshot(ordered, byId);
        return ordered;
    }

    private @Nullable String normalizeCode(@Nullable String code) {
        if (code == null || code.isBlank()) {
            return null;
        }
        return code.trim().toUpperCase(Locale.ROOT);
    }
}
