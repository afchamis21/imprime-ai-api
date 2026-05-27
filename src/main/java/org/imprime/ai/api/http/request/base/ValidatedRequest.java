package org.imprime.ai.api.http.request.base;

import org.imprime.ai.api.model.enums.MessageCd;
import org.imprime.ai.api.model.exception.BadRequestException;

public interface ValidatedRequest {
    MessageCd getInvalidReason();

    default void validateOrThrow() {
        MessageCd invalidReason = getInvalidReason();
        if (invalidReason != null) {
            throw new BadRequestException(invalidReason);
        }
    }
}
