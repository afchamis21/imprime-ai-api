package org.imprime.ai.api.quote;

import org.imprime.ai.api.util.JsonCodec;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;

public final class QuoteConfigReader {
    public static final int INTERNAL_SCALE = 8;
    public static final BigDecimal HUNDRED = new BigDecimal("100");
    public static final BigDecimal THOUSAND = new BigDecimal("1000");
    public static final BigDecimal SIXTY = new BigDecimal("60");
    public static final BigDecimal MAX_COMBINED_PERCENT = new BigDecimal("95.00");

    private QuoteConfigReader() {
    }

    public static ObjectNode mergeConfigs(
            JsonCodec codec,
            String defaultConfigJson,
            String makerConfigJson,
            Map<String, Object> overrideConfig) {
        ObjectNode merged = codec.createObjectNode();
        mergeObjectNode(merged, codec.readTree(defaultConfigJson));
        mergeObjectNode(merged, codec.readTree(makerConfigJson));
        if (overrideConfig != null && !overrideConfig.isEmpty()) {
            for (Map.Entry<String, Object> entry : overrideConfig.entrySet()) {
                if (entry.getKey() == null || entry.getValue() == null) {
                    continue;
                }
                JsonNode val = codec.valueToTree(entry.getValue());
                if (val != null && !val.isNull()) {
                    merged.set(entry.getKey(), val);
                }
            }
        }
        return merged;
    }

    private static void mergeObjectNode(ObjectNode target, JsonNode source) {
        if (source == null || !source.isObject()) {
            return;
        }
        for (Map.Entry<String, JsonNode> entry : source.properties()) {
            JsonNode value = entry.getValue();
            if (value != null && !value.isNull()) {
                target.set(entry.getKey(), value);
            }
        }
    }

    public static boolean hasNonNullKey(JsonNode node, String key) {
        if (node == null || key == null) {
            return false;
        }
        JsonNode value = node.get(key);
        return value != null && !value.isNull() && !value.isMissingNode();
    }

    public static BigDecimal getBigDecimal(JsonNode node, String key, BigDecimal fallback) {
        if (!hasNonNullKey(node, key)) {
            return fallback;
        }
        JsonNode value = node.get(key);
        if (value.isNumber()) {
            return value.decimalValue();
        }
        if (value.isString()) {
            try {
                return new BigDecimal(value.asString().trim());
            } catch (NumberFormatException ignored) {
                return fallback;
            }
        }
        return fallback;
    }

    public static BigDecimal getNonNegativeBigDecimal(JsonNode node, String key, BigDecimal fallback) {
        return sanitizeNonNegative(getBigDecimal(node, key, fallback), fallback);
    }

    public static int getInt(JsonNode node, String key, int fallback) {
        if (!hasNonNullKey(node, key)) {
            return fallback;
        }
        JsonNode value = node.get(key);
        if (value.isNumber()) {
            return value.asInt();
        }
        if (value.isString()) {
            try {
                return new BigDecimal(value.asString().trim()).intValue();
            } catch (NumberFormatException ignored) {
                return fallback;
            }
        }
        return fallback;
    }

    public static boolean getBoolean(JsonNode node, String key, boolean fallback) {
        if (!hasNonNullKey(node, key)) {
            return fallback;
        }
        JsonNode value = node.get(key);
        if (value.isBoolean()) {
            return value.asBoolean();
        }
        if (value.isString()) {
            String raw = value.asString().trim();
            if ("true".equalsIgnoreCase(raw)) {
                return true;
            }
            if ("false".equalsIgnoreCase(raw)) {
                return false;
            }
        }
        return fallback;
    }

    public static String getString(JsonNode node, String key, String fallback) {
        if (!hasNonNullKey(node, key)) {
            return fallback;
        }
        JsonNode value = node.get(key);
        if (value.isString()) {
            return value.asString();
        }
        String text = value.asString();
        return text != null ? text : fallback;
    }

    public static BigDecimal sanitizeNonNegative(BigDecimal value, BigDecimal fallback) {
        if (value == null || value.compareTo(BigDecimal.ZERO) < 0) {
            return fallback;
        }
        return value;
    }

    public static BigDecimal sanitizeNonNegative(BigDecimal value) {
        return sanitizeNonNegative(value, BigDecimal.ZERO);
    }

    public static BigDecimal clampPercent(BigDecimal value) {
        BigDecimal nonNegative = sanitizeNonNegative(value, BigDecimal.ZERO);
        return nonNegative.compareTo(HUNDRED) > 0 ? HUNDRED : nonNegative;
    }

    public static BigDecimal divide(BigDecimal numerator, BigDecimal denominator) {
        if (numerator == null || denominator == null || denominator.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }
        return numerator.divide(denominator, INTERNAL_SCALE, RoundingMode.HALF_UP);
    }

    public static BigDecimal scale(BigDecimal value, int targetScale) {
        BigDecimal zeroScaled = BigDecimal.ZERO.setScale(targetScale, RoundingMode.HALF_UP);
        if (value == null) {
            return zeroScaled;
        }
        BigDecimal scaled = value.setScale(targetScale, RoundingMode.HALF_UP);
        return scaled.compareTo(BigDecimal.ZERO) == 0 ? zeroScaled : scaled;
    }

    public static BigDecimal scale2(BigDecimal value) {
        return scale(value, 2);
    }

    public static BigDecimal negateSafe(BigDecimal value) {
        if (value == null || value.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }
        return value.negate();
    }

    public static String formatBrl(BigDecimal value) {
        BigDecimal scaled = scale2(value);
        return "R$ " + scaled.toPlainString().replace('.', ',');
    }

    public static String formatDecimalPtBr(BigDecimal value, int targetScale) {
        return scale(value, targetScale).toPlainString().replace('.', ',');
    }

    public static String formatCompactPtBr(BigDecimal value, int maxDecimals) {
        BigDecimal scaled = scale(value, maxDecimals);
        BigDecimal stripped = scaled.stripTrailingZeros();
        String plain = stripped.scale() < 0
                ? stripped.setScale(0, RoundingMode.HALF_UP).toPlainString()
                : stripped.toPlainString();
        return plain.replace('.', ',');
    }

    public static String formatDuration(BigDecimal hours) {
        long totalMinutes = sanitizeNonNegative(hours)
                .multiply(SIXTY)
                .setScale(0, RoundingMode.HALF_UP)
                .longValue();
        long h = totalMinutes / 60;
        long m = totalMinutes % 60;
        if (h > 0 && m > 0) {
            return h + "h " + m + "min";
        }
        if (h > 0) {
            return h + "h";
        }
        return m + "min";
    }
}
