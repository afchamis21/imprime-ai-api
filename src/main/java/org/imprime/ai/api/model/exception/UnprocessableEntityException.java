package org.imprime.ai.api.model.exception;

import org.imprime.ai.api.model.enums.MessageCd;
import org.springframework.http.HttpStatus;

public class UnprocessableEntityException extends HttpException {
    private static final HttpStatus httpStatus = HttpStatus.UNPROCESSABLE_ENTITY;

    public UnprocessableEntityException(MessageCd messageCd, String... params) {
        super(messageCd, httpStatus, params);
    }

    public UnprocessableEntityException(String message, MessageCd messageCd, String... params) {
        super(message, messageCd, httpStatus, params);
    }
}
