package org.imprime.ai.api.repo.cache;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Ticker;
import org.imprime.ai.api.model.Company;
import org.imprime.ai.api.model.User;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

/**
 * In-memory TTL cache tracking real-time online presence of maker studios by company ID.
 * Uses Caffeine directly (without ServiceContext) so HTTP and WebSocket threads can update
 * activity safely without leaking ThreadLocal context.
 */
@Repository
public class MakerPresenceCache {
    private static final Duration DEFAULT_TTL = Duration.ofMinutes(4);
    private static final long DEFAULT_MAX_SIZE = 10_000L;

    private final Duration ttl;
    private final Clock clock;
    private final Cache<Long, Instant> cache;

    @Autowired
    public MakerPresenceCache(
            @Value("${imprime.ai.api.presence.ttl:4m}") Duration ttl,
            @Value("${imprime.ai.api.presence.max-size:10000}") long maxSize) {
        this(ttl, maxSize, Ticker.systemTicker(), Clock.systemUTC());
    }

    public MakerPresenceCache(Duration ttl, long maxSize, Ticker ticker, Clock clock) {
        this.ttl = (ttl == null || ttl.isZero() || ttl.isNegative()) ? DEFAULT_TTL : ttl;
        long effectiveMaxSize = maxSize <= 0 ? DEFAULT_MAX_SIZE : maxSize;
        Ticker effectiveTicker = ticker != null ? ticker : Ticker.systemTicker();
        this.clock = clock != null ? clock : Clock.systemUTC();
        this.cache = Caffeine.newBuilder()
                .maximumSize(effectiveMaxSize)
                .expireAfterWrite(this.ttl)
                .ticker(effectiveTicker)
                .build();
    }

    public @Nullable Instant markOnline(@Nullable Long companyId) {
        if (companyId == null) {
            return null;
        }
        Instant now = clock.instant();
        cache.put(companyId, now);
        return now;
    }

    public void markUserActivity(@Nullable User user) {
        if (user != null && user.getCompanyId() != null) {
            markOnline(user.getCompanyId());
        }
    }

    public void markOffline(@Nullable Long companyId) {
        if (companyId != null) {
            cache.invalidate(companyId);
        }
    }

    public void markUserOffline(@Nullable User user) {
        if (user != null && user.getCompanyId() != null) {
            markOffline(user.getCompanyId());
        }
    }

    public boolean isOnline(@Nullable Long companyId) {
        return companyId != null && cache.getIfPresent(companyId) != null;
    }

    public boolean isCompanyOnline(@Nullable Company company) {
        return company != null
                && !Boolean.TRUE.equals(company.getVacationMode())
                && isOnline(company.getId());
    }

    public Optional<Instant> getLastSeenAt(@Nullable Long companyId) {
        if (companyId == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(cache.getIfPresent(companyId));
    }

    public Duration getTtl() {
        return ttl;
    }
}
