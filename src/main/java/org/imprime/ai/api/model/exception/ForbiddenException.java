package org.imprime.ai.api.model.exception;

import org.imprime.ai.api.model.enums.MessageCd;
import org.springframework.http.HttpStatus;

public class ForbiddenException extends HttpException {
    public ForbiddenException() {
        super(MessageCd.FORBIDDEN, HttpStatus.FORBIDDEN, new String[0]);
    }
}
