package org.imprime.ai.api.validator;

import org.imprime.ai.api.model.enums.MessageCd;
import org.jspecify.annotations.Nullable;

import java.util.regex.Pattern;

public class RgValidator implements IValidator<String> {
    private static final Pattern RG_PATTERN = Pattern.compile("^\\d{8}[0-9Xx]$");

    @Override
    @Nullable
    public MessageCd validate(String value) {
        boolean valid = isValid(value);
        if (valid) {
            return null;
        }

        return MessageCd.INVALID_RG;
    }


    public static boolean isValid(String rg) {
        if (rg == null || rg.isBlank()) {
            return false;
        }

        rg = rg.replaceAll("[.\\-\\s]", "");

        return RG_PATTERN.matcher(rg).matches();
    }
}
