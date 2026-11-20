package org.imprime.ai.api.model.exception;

import org.imprime.ai.api.model.enums.MessageCd;
import org.springframework.http.HttpStatus;

public class BadGatewayException extends HttpException {
    private static final HttpStatus httpStatus = HttpStatus.BAD_GATEWAY;

    public BadGatewayException(MessageCd messageCd, String... params) {
        super(messageCd, httpStatus, params);
    }

    public BadGatewayException(String message, MessageCd messageCd, String... params) {
        super(message, messageCd, httpStatus, params);
    }
}
