package org.imprime.ai.api.repo.cache;

import lombok.extern.slf4j.Slf4j;
import org.imprime.ai.api.model.MakerQuoteSettings;
import org.imprime.ai.api.repo.cache.base.Cached;
import org.imprime.ai.api.repo.cache.base.DynamicInMemoryCache;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Slf4j
@Repository
public class MakerQuoteSettingsInMemoryCache extends DynamicInMemoryCache<Long, List<MakerQuoteSettings>> {

    public static final long MAX_SIZE = 2_000L;
    public static final Duration TTL = Duration.ofMinutes(15);

    public MakerQuoteSettingsInMemoryCache() {
        super(MAX_SIZE, TTL);
    }

    public Optional<List<MakerQuoteSettings>> findByCompanyId(Long companyId) {
        if (companyId == null) {
            return Optional.empty();
        }
        return super.get(companyId).map(Cached::value);
    }

    public void putCompanySettings(Long companyId, List<MakerQuoteSettings> settings) {
        if (companyId == null || settings == null) {
            return;
        }
        List<MakerQuoteSettings> immutableCopy = settings.stream()
                .filter(Objects::nonNull)
                .toList();
        super.put(companyId, immutableCopy);
    }

    public void evict(Long companyId) {
        if (companyId == null) {
            return;
        }
        super.remove(companyId);
    }

    public void evictAfterCommit(Long companyId) {
        if (companyId == null) {
            return;
        }
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    evict(companyId);
                }
            });
        } else {
            evict(companyId);
        }
    }
}
