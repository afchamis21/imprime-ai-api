package org.imprime.ai.api.util;

import org.imprime.ai.api.model.enums.PixKeyType;

public final class MaskingUtil {
    private MaskingUtil() {
    }

    public static String maskPixKey(PixKeyType type, String rawKey) {
        if (rawKey == null || rawKey.isBlank()) {
            return null;
        }
        String trimmed = rawKey.trim();
        if (type == PixKeyType.EMAIL) {
            int atIndex = trimmed.indexOf('@');
            if (atIndex > 0) {
                String local = trimmed.substring(0, atIndex);
                String domain = trimmed.substring(atIndex);
                String visible = local.substring(0, Math.min(3, local.length()));
                return visible + "***" + domain;
            }
        }
        if (type == PixKeyType.RANDOM) {
            if (trimmed.length() <= 8) {
                return "***";
            }
            return trimmed.substring(0, 4) + "***" + trimmed.substring(trimmed.length() - 4);
        }
        if (trimmed.length() <= 5) {
            return "***";
        }
        return trimmed.substring(0, 3) + "***" + trimmed.substring(trimmed.length() - 2);
    }

    public static String maskDocument(String rawDocument) {
        if (rawDocument == null || rawDocument.isBlank()) {
            return null;
        }
        String digits = rawDocument.replaceAll("\\D", "");
        if (digits.length() == 11) {
            return "***." + digits.substring(3, 6) + "." + digits.substring(6, 9) + "-**";
        }
        if (digits.length() == 14) {
            return "**." + digits.substring(2, 5) + "." + digits.substring(5, 8) + "/" + digits.substring(8, 12) + "-**";
        }
        String trimmed = rawDocument.trim();
        if (trimmed.length() <= 4) {
            return "****";
        }
        return trimmed.substring(0, 2) + "***" + trimmed.substring(trimmed.length() - 2);
    }
}
