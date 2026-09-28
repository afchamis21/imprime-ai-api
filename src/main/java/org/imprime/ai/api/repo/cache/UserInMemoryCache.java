package org.imprime.ai.api.repo.cache;

import lombok.Builder;
import lombok.extern.slf4j.Slf4j;
import org.imprime.ai.api.model.User;
import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

/**
 * Normally I'd have made a cache interface, an abstract class, an ICacheable, etc. etc.
 * But I'm in a hurry.
 * <p>
 * As the app grows, this would be better stored on REDIS but it works for now! Or have some invalidation strategies
 * <p>
 * Honestly, In Memmory is probably OK, but I can make this better later for sure
 */
@Slf4j
@Repository
public class UserInMemoryCache extends DynamicInMemoryCache<UserInMemoryCache.Key, User> {

    protected UserInMemoryCache() {
        super(1_000L, Duration.of(10, ChronoUnit.MINUTES));
    }

    public Optional<Cached<User>> findUserByGuid(String guid) {
        return get(new Key(null, guid));
    }

    public Optional<Cached<User>> findUserById(Long id) {
        return get(new Key(id, null));
    }

    public void put(User user) {
        if (user == null) return;

        super.put(Key.ofUserId(user.getId()), user);
        super.put(Key.ofGuid(user.getGuid()), user);
    }

    @Builder
    public record Key(Long userId, String guid) {
        public static Key full(Long userId, String guid) {
            return new Key(userId, guid);
        }

        public static Key ofGuid(String guid) {
            return new Key(null, guid);
        }

        public static Key ofUserId(Long userId) {
            return new Key(userId, null);
        }
    }
}
