package org.imprime.ai.api.util;

import java.text.Normalizer;
import java.util.Locale;
import java.util.function.Predicate;
import java.util.regex.Pattern;

public final class SlugUtil {
    private static final Pattern NON_ASCII_COMBINING = Pattern.compile("\\p{InCombiningDiacriticalMarks}+");
    private static final Pattern NON_ALNUM = Pattern.compile("[^a-z0-9]+");
    private static final Pattern EDGE_HYPHENS = Pattern.compile("^-+|-+$");
    private static final int MAX_SLUG_LENGTH = 160;

    private SlugUtil() {
    }

    public static String slugify(String raw) {
        if (raw == null || raw.isBlank()) {
            return "item";
        }
        String normalized = Normalizer.normalize(raw.trim(), Normalizer.Form.NFD);
        String ascii = NON_ASCII_COMBINING.matcher(normalized).replaceAll("");
        String lowered = ascii.toLowerCase(Locale.ROOT);
        String hyphenated = NON_ALNUM.matcher(lowered).replaceAll("-");
        String trimmed = EDGE_HYPHENS.matcher(hyphenated).replaceAll("");
        if (trimmed.isEmpty()) {
            return "item";
        }
        if (trimmed.length() > MAX_SLUG_LENGTH) {
            trimmed = EDGE_HYPHENS.matcher(trimmed.substring(0, MAX_SLUG_LENGTH)).replaceAll("");
            if (trimmed.isEmpty()) {
                return "item";
            }
        }
        return trimmed;
    }

    public static String ensureUniqueSlug(String baseName, Predicate<String> existsCheck) {
        String base = slugify(baseName);
        if (!existsCheck.test(base)) {
            return base;
        }
        int suffix = 2;
        while (true) {
            String candidate = base + "-" + suffix;
            if (!existsCheck.test(candidate)) {
                return candidate;
            }
            suffix++;
        }
    }
}
