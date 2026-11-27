package org.imprime.ai.api.notification.chat;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Ticker;
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
 * Cache de presença em memória por usuário (Compradores, Makers e Admins) indexado por {@code userGuid}.
 */
@Repository
public class UserPresenceCache {
    private static final Duration DEFAULT_TTL = Duration.ofMinutes(3);
    private static final long DEFAULT_MAX_SIZE = 25_000L;

    private final Duration ttl;
    private final Clock clock;
    private final Cache<String, Instant> cache;

    @Autowired
    public UserPresenceCache(
            @Value("${imprime.ai.notification.user-presence-ttl:3m}") Duration ttl,
            @Value("${imprime.ai.api.presence.max-size:25000}") long maxSize) {
        this(ttl, maxSize, Ticker.systemTicker(), Clock.systemUTC());
    }

    public UserPresenceCache(Duration ttl, long maxSize, Ticker ticker, Clock clock) {
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

    public void markActive(@Nullable User user) {
        if (user != null && user.getGuid() != null && !user.getGuid().isBlank()) {
            cache.put(user.getGuid(), clock.instant());
        }
    }

    public void markOffline(@Nullable User user) {
        if (user != null && user.getGuid() != null && !user.getGuid().isBlank()) {
            cache.invalidate(user.getGuid());
        }
    }

    public boolean isOnline(@Nullable String userGuid) {
        return userGuid != null && !userGuid.isBlank() && cache.getIfPresent(userGuid) != null;
    }

    public Optional<Instant> getLastSeenAt(@Nullable String userGuid) {
        if (userGuid == null || userGuid.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(cache.getIfPresent(userGuid));
    }

    public Duration getTtl() {
        return ttl;
    }
}
