package org.imprime.ai.api.model.exception;

import org.imprime.ai.api.model.enums.MessageCd;
import org.springframework.http.HttpStatus;

public class ConflictException extends HttpException {
    private static final HttpStatus httpStatus = HttpStatus.CONFLICT;

    public ConflictException(MessageCd messageCd, String... params) {
        super(messageCd, httpStatus, params);
    }

    public ConflictException(String message, MessageCd messageCd, String... params) {
        super(message, messageCd, httpStatus, params);
    }
}
