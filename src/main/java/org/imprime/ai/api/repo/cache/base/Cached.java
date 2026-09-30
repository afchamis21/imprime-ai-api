package org.imprime.ai.api.repo.cache.base;

import java.time.Instant;

public record Cached<V>(V value, Metadata metadata) {
    public record Metadata(Instant cachedAt, String transactionId) {}
}
