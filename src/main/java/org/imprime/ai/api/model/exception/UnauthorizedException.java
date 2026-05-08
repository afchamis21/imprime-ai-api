package org.imprime.ai.api.model.exception;

import org.imprime.ai.api.model.enums.MessageCd;
import org.springframework.http.HttpStatus;

public class UnauthorizedException extends HttpException {
    private static final HttpStatus httpStatus = HttpStatus.UNAUTHORIZED;
    private static final MessageCd messageCd = MessageCd.UNAUTHORIZED;

    public UnauthorizedException(String message) {
        super(message, messageCd, httpStatus);
    }

    public UnauthorizedException() {
        super(messageCd, httpStatus);
    }
}
