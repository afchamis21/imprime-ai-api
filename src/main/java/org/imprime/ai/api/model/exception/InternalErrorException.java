package org.imprime.ai.api.model.exception;

import org.imprime.ai.api.model.enums.MessageCd;
import org.springframework.http.HttpStatus;

public class InternalErrorException extends HttpException {
    private static final MessageCd messageCd = MessageCd.INTERNAL_SERVER_ERROR;
    private static final HttpStatus httpStatus = HttpStatus.INTERNAL_SERVER_ERROR;

    public InternalErrorException(String message) {
        super(message, messageCd, httpStatus);
    }

    public InternalErrorException() {
        super(messageCd, httpStatus);
    }
}
