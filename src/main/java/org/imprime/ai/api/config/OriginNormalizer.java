package org.imprime.ai.api.config;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Utility helpers to normalize comma-separated or list-based configuration values
 * such as allowed CORS/WebSocket origins, HTTP methods, and exposed headers.
 */
public final class OriginNormalizer {

    private OriginNormalizer() {
    }

    /**
     * Splits comma-separated origin entries, trims whitespace, strips trailing slashes,
     * discards blanks, and preserves insertion order without duplicates.
     */
    public static String[] normalizeOrigins(Collection<String> rawOrigins) {
        return normalize(rawOrigins, true);
    }

    /**
     * Splits comma-separated entries, trims whitespace, discards blanks,
     * and preserves insertion order without duplicates.
     */
    public static String[] normalizeValues(Collection<String> rawValues) {
        return normalize(rawValues, false);
    }

    private static String[] normalize(Collection<String> rawEntries, boolean stripTrailingSlashes) {
        if (rawEntries == null || rawEntries.isEmpty()) {
            return new String[0];
        }

        Set<String> normalized = new LinkedHashSet<>();
        for (String entry : rawEntries) {
            if (entry == null) {
                continue;
            }
            for (String part : entry.split(",")) {
                String trimmed = part.trim();
                if (stripTrailingSlashes) {
                    trimmed = trimmed.replaceAll("/+$", "");
                }
                if (!trimmed.isBlank()) {
                    normalized.add(trimmed);
                }
            }
        }
        return normalized.toArray(String[]::new);
    }
}
