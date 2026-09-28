package org.imprime.ai.api.http.request.base;

import jakarta.annotation.Nullable;
import org.imprime.ai.api.model.enums.MessageCd;
import org.imprime.ai.api.model.exception.BadRequestException;
import org.imprime.ai.api.validator.reflection.ValidatorEngine;

public interface ValidatedRequest {
    default void validateOrThrow() throws Exception {
        MessageCd invalidReason = getInvalidReason();
        if (invalidReason != null) {
            throw new BadRequestException(invalidReason);
        }
    }

    @Nullable
    default MessageCd getInvalidReason() throws Exception {
        return ValidatorEngine.validate(this);
    }
}
