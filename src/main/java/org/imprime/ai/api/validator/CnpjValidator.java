package org.imprime.ai.api.validator;

import org.imprime.ai.api.model.enums.MessageCd;
import org.jspecify.annotations.Nullable;

public class CnpjValidator implements IValidator<String> {
    @Override
    @Nullable
    public MessageCd validate(String value) {
        return null;
    }
}
