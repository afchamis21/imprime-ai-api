package org.imprime.ai.api.validator;

import org.imprime.ai.api.model.enums.MessageCd;
import org.jspecify.annotations.Nullable;

public class CpfValidator implements IValidator<String> {
    @Override
    @Nullable
    public MessageCd validate(String value) {
        boolean valid = isValid(value);
        if (valid) {
            return null;
        }

        return MessageCd.INVALID_CPF;
    }

    public static boolean isValid(String cpf) {
        if (cpf == null) {
            return false;
        }

        cpf = cpf.replaceAll("\\D", "");

        if (!cpf.matches("\\d{11}")) {
            return false;
        }

        if (cpf.chars().distinct().count() == 1) {
            return false;
        }

        int digit1 = calculateDigit(cpf.substring(0, 9), 10);
        int digit2 = calculateDigit(cpf.substring(0, 9) + digit1, 11);

        return cpf.equals(cpf.substring(0, 9) + digit1 + digit2);
    }

    private static int calculateDigit(String value, int weight) {
        int sum = 0;

        for (int i = 0; i < value.length(); i++) {
            sum += Character.getNumericValue(value.charAt(i)) * (weight - i);
        }

        int remainder = sum % 11;

        return remainder < 2 ? 0 : 11 - remainder;
    }
}
