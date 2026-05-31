package org.imprime.ai.api.validator;

import jakarta.annotation.Nullable;
import org.imprime.ai.api.model.enums.MessageCd;

public interface IValidator<T> {

    @Nullable
    MessageCd validate(T value);
}
