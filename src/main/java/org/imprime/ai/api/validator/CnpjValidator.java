package org.imprime.ai.api.validator;

import org.imprime.ai.api.model.enums.MessageCd;
import org.jspecify.annotations.Nullable;

public class CnpjValidator implements IValidator<String> {
    private static final int[] FIRST_WEIGHTS =
            {5, 4, 3, 2, 9, 8, 7, 6, 5, 4, 3, 2};

    private static final int[] SECOND_WEIGHTS =
            {6, 5, 4, 3, 2, 9, 8, 7, 6, 5, 4, 3, 2};

    @Override
    @Nullable
    public MessageCd validate(String value) {
        boolean valid = isValid(value);
        if (valid) {
            return null;
        }

        return MessageCd.INVALID_CNPJ;
    }

    public static boolean isValid(String cnpj) {
        if (cnpj == null) {
            return false;
        }

        cnpj = cnpj.replaceAll("\\D", "");

        if (!cnpj.matches("\\d{14}")) {
            return false;
        }

        if (cnpj.chars().distinct().count() == 1) {
            return false;
        }

        int digit1 = calculateDigit(cnpj.substring(0, 12), FIRST_WEIGHTS);
        int digit2 = calculateDigit(
                cnpj.substring(0, 12) + digit1,
                SECOND_WEIGHTS);

        return cnpj.equals(cnpj.substring(0, 12) + digit1 + digit2);
    }

    private static int calculateDigit(String value, int[] weights) {
        int sum = 0;

        for (int i = 0; i < weights.length; i++) {
            sum += Character.getNumericValue(value.charAt(i)) * weights[i];
        }

        int remainder = sum % 11;

        return remainder < 2 ? 0 : 11 - remainder;
    }
}
