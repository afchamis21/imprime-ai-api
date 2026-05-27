package org.imprime.ai.api.model.exception;

import org.imprime.ai.api.model.enums.MessageCd;
import org.springframework.http.HttpStatus;

public class BadRequestException extends HttpException {
    private static final HttpStatus httpStatus = HttpStatus.BAD_REQUEST;

    public BadRequestException(String message, MessageCd messageCd) {
        super(message, messageCd, httpStatus);
    }

    public BadRequestException(MessageCd messageCd) {
        super(messageCd, httpStatus);
    }
}
