package org.imprime.ai.api.model.exception;

import org.imprime.ai.api.model.enums.MessageCd;
import org.springframework.http.HttpStatus;

public class NotFoundException extends HttpException {
    private static final HttpStatus httpStatus = HttpStatus.NOT_FOUND;

    public NotFoundException(MessageCd messageCd, String... params) {
        super(messageCd, httpStatus, params);
    }

    public NotFoundException(String message, MessageCd messageCd, String... params) {
        super(message, messageCd, httpStatus, params);
    }
}
