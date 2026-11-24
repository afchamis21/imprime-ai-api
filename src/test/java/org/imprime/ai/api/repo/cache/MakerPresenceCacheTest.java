package org.imprime.ai.api.repo.cache;

import com.github.benmanes.caffeine.cache.Ticker;
import org.imprime.ai.api.model.Company;
import org.imprime.ai.api.model.User;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class MakerPresenceCacheTest {

    @Test
    void tracksOnlineStateExpiresAfterTtlAndRenewsOnActivity() {
        AtomicLong nanos = new AtomicLong(0L);
        AtomicReference<Instant> currentInstant = new AtomicReference<>(Instant.parse("2026-11-23T22:00:00Z"));
        Ticker fakeTicker = nanos::get;
        Clock fakeClock = new Clock() {
            @Override
            public ZoneOffset getZone() {
                return ZoneOffset.UTC;
            }

            @Override
            public Clock withZone(java.time.ZoneId zone) {
                return this;
            }

            @Override
            public Instant instant() {
                return currentInstant.get();
            }
        };

        MakerPresenceCache cache = new MakerPresenceCache(Duration.ofMinutes(4), 100L, fakeTicker, fakeClock);

        assertFalse(cache.isOnline(10L));
        assertTrue(cache.getLastSeenAt(10L).isEmpty());
        assertNull(cache.markOnline(null));

        Instant firstSeen = cache.markOnline(10L);
        assertEquals(Instant.parse("2026-11-23T22:00:00Z"), firstSeen);
        assertTrue(cache.isOnline(10L));
        assertEquals(firstSeen, cache.getLastSeenAt(10L).orElseThrow());

        // Advance 3m (< 4m TTL) and renew activity
        nanos.addAndGet(Duration.ofMinutes(3).toNanos());
        currentInstant.set(Instant.parse("2026-11-23T22:03:00Z"));
        assertTrue(cache.isOnline(10L));

        Instant renewedSeen = cache.markOnline(10L);
        assertEquals(Instant.parse("2026-11-23T22:03:00Z"), renewedSeen);

        // Advance another 3m (6m since start, but only 3m since renewal) -> still online
        nanos.addAndGet(Duration.ofMinutes(3).toNanos());
        currentInstant.set(Instant.parse("2026-11-23T22:06:00Z"));
        assertTrue(cache.isOnline(10L));

        // Advance another 2m (5m since renewal > 4m TTL) -> expired
        nanos.addAndGet(Duration.ofMinutes(2).toNanos());
        assertFalse(cache.isOnline(10L));
        assertTrue(cache.getLastSeenAt(10L).isEmpty());
    }

    @Test
    void handlesOfflineRemovalVacationModeAndNonMakerUsers() {
        MakerPresenceCache cache = new MakerPresenceCache(Duration.ofMinutes(4), 100L);

        User buyer = new User();
        buyer.setId(1L);
        buyer.setCompanyId(null);
        cache.markUserActivity(buyer);
        cache.markUserActivity(null);

        User maker = new User();
        maker.setId(2L);
        maker.setCompanyId(55L);
        cache.markUserActivity(maker);

        Company activeCompany = new Company();
        activeCompany.setId(55L);
        activeCompany.setVacationMode(false);

        assertTrue(cache.isOnline(55L));
        assertTrue(cache.isCompanyOnline(activeCompany));

        activeCompany.setVacationMode(true);
        assertFalse(cache.isCompanyOnline(activeCompany));

        activeCompany.setVacationMode(false);
        cache.markUserOffline(maker);
        assertFalse(cache.isOnline(55L));
        assertFalse(cache.isCompanyOnline(activeCompany));
        assertFalse(cache.isCompanyOnline(null));
    }

    @Test
    void usesSafeDefaultsWhenConfiguredWithNonPositiveValues() {
        MakerPresenceCache cache = new MakerPresenceCache(Duration.ZERO, 0L, null, null);
        assertEquals(Duration.ofMinutes(4), cache.getTtl());
        assertNotNull(cache.markOnline(99L));
        assertTrue(cache.isOnline(99L));
        cache.markOffline(99L);
        assertFalse(cache.isOnline(99L));
    }
}
