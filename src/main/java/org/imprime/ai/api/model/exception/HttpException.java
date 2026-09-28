package org.imprime.ai.api.model.exception;

import lombok.Getter;
import org.imprime.ai.api.http.ServiceContext;
import org.imprime.ai.api.model.enums.MessageCd;
import org.springframework.http.HttpStatus;

import java.util.Objects;

@Getter
public class HttpException extends RuntimeException {
    private final MessageCd messageCd;
    private final HttpStatus httpStatus;
    private final String[] args;

    public HttpException(MessageCd messageCd, HttpStatus httpStatus, String[] args) {
        this.messageCd = messageCd;
        this.httpStatus = httpStatus;
        this.args = Objects.requireNonNullElse(args, new String[0]);
        storeInContext();
    }

    public HttpException(String message, MessageCd messageCd, HttpStatus httpStatus, String[] args) {
        super(message);
        this.messageCd = messageCd;
        this.httpStatus = httpStatus;
        this.args = Objects.requireNonNullElse(args, new String[0]);
        storeInContext();
    }

    private void storeInContext() {
        ServiceContext.addException(this);
    }
}
