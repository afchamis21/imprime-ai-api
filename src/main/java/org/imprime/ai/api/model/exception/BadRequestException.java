package org.imprime.ai.api.model.exception;

import org.imprime.ai.api.model.enums.MessageCd;
import org.springframework.http.HttpStatus;

public class BadRequestException extends HttpException {
    private static final HttpStatus httpStatus = HttpStatus.BAD_REQUEST;

    public BadRequestException(String message, MessageCd messageCd, String ...params) {
        super(message, messageCd, httpStatus, params);
    }

    public BadRequestException(MessageCd messageCd, String ...params) {
        super(messageCd, httpStatus, params);
    }
}
