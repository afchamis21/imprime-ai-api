package org.imprime.ai.api.repo.cache.base;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.imprime.ai.api.http.ServiceContext;
import org.jspecify.annotations.Nullable;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

public abstract class DynamicInMemoryCache<Key, Value> {
    private final Cache<Key, Cached<Value>> cache;

    protected DynamicInMemoryCache(long maxSize, Duration ttl) {
        this.cache = Caffeine.newBuilder().maximumSize(maxSize).expireAfterWrite(ttl).build();
    }

    public Optional<Cached<Value>> get(Key key) {
        return Optional.ofNullable(cache.getIfPresent(key));
    }

    public @Nullable Cached<Value> put(Key key, Value value) {
        if (key == null || value == null) return null;

        Cached<Value> cached = new Cached<>(value, new Cached.Metadata(Instant.now(), ServiceContext.getContext().getTransactionId()));
        cache.put(key, cached);

        return cached;
    }

    public void remove(Key key) {
        if (key == null) return;
        cache.invalidate(key);
    }
}
