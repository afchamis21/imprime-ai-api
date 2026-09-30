package org.imprime.ai.api.repo.cache.base;

import lombok.extern.slf4j.Slf4j;
import org.imprime.ai.api.http.ServiceContext;
import org.jspecify.annotations.Nullable;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

@Slf4j
@EnableScheduling
public abstract class StaticInMemoryCache<Key, Value> {
    private ConcurrentHashMap<Key, Cached<Value>> cache = new ConcurrentHashMap<>();

    protected abstract @Nullable Key getKey(@Nullable Value value);
    protected abstract @Nullable List<Value> loadAll();

    protected void load() {
        String cacheName = getClass().getSimpleName();
        log.info("Loading [{}]...", cacheName);

        List<Value> values = loadAll();
        if (values == null) {
            log.warn("Load All returned Null! Skipping Cache Load...");
            return;
        }

        ConcurrentHashMap<Key, Cached<Value>> map = new ConcurrentHashMap<>(values.size());
        for (Value value : values) {
            Key key = getKey(value);
            if (key == null) {
                log.warn("Value [{}] produced a null key on Cache [{}]!", value, getClass().getSimpleName());
                continue;
            }

            Cached<Value> cached = new Cached<>(value, new Cached.Metadata(Instant.now(), ServiceContext.getContext().getTransactionId()));
            map.put(key, cached);
        }

        log.info("Loaded [{}/{}] values for [{}]", values.size(), map.size(), cacheName);

        this.cache = map;
    }

    public Optional<Cached<Value>> get(Key key) {
        return Optional.ofNullable(cache.get(key));
    }

    public @Nullable Cached<Value> put(Value value) {
        Key key = getKey(value);
        if (key == null) return null;

        Cached<Value> cached = new Cached<>(value, new Cached.Metadata(Instant.now(), ServiceContext.getContext().getTransactionId()));
        cache.put(key, cached);

        return cached;
    }

    public void removeByKey(Key key) {
        if (key == null) return;
        cache.remove(key);
    }

    public void removeByValue(Value value) {
        Key key = getKey(value);
        removeByKey(key);
    }

    @Scheduled(fixedRate = 5, timeUnit = TimeUnit.MINUTES)
    protected void reload() {
        try {
            ServiceContext.getContext();
            load();
        } catch (Exception e) {
            log.error("Failed to load Cache [{}]!", getClass().getSimpleName(), e);
        } finally {
            ServiceContext.clear();
        }
    }
}
