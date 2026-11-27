package org.imprime.ai.api.notification.chat;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Ticker;
import org.imprime.ai.api.notification.config.NotificationProperties;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/**
 * Cache de TTL curto indicando se o usuário visualizou recentemente uma conversa específica.
 */
@Repository
public class ChatActiveViewCache {
    private static final Duration DEFAULT_TTL = Duration.ofSeconds(90);

    private final Clock clock;
    private final Cache<String, Instant> activeViews;

    @Autowired
    public ChatActiveViewCache(NotificationProperties properties) {
        this(properties != null && properties.getChat() != null
                        ? properties.getChat().getActiveViewTtl()
                        : DEFAULT_TTL,
                Ticker.systemTicker(),
                Clock.systemUTC());
    }

    public ChatActiveViewCache(Duration ttl, Ticker ticker, Clock clock) {
        Duration effectiveTtl = (ttl == null || ttl.isZero() || ttl.isNegative()) ? DEFAULT_TTL : ttl;
        Ticker effectiveTicker = ticker != null ? ticker : Ticker.systemTicker();
        this.clock = clock != null ? clock : Clock.systemUTC();
        this.activeViews = Caffeine.newBuilder()
                .maximumSize(50_000L)
                .expireAfterWrite(effectiveTtl)
                .ticker(effectiveTicker)
                .build();
    }

    public void markViewing(@Nullable String userGuid, @Nullable UUID chatId) {
        if (userGuid == null || userGuid.isBlank() || chatId == null) {
            return;
        }
        activeViews.put(key(userGuid, chatId), clock.instant());
    }

    public boolean isViewing(@Nullable String userGuid, @Nullable UUID chatId) {
        if (userGuid == null || userGuid.isBlank() || chatId == null) {
            return false;
        }
        return activeViews.getIfPresent(key(userGuid, chatId)) != null;
    }

    private static String key(String userGuid, UUID chatId) {
        return userGuid + ":" + chatId;
    }
}
