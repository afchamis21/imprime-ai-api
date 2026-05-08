package org.imprime.ai.api.model.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.imprime.ai.api.model.enums.MessageCd;
import org.springframework.http.HttpStatus;

@Getter
public class HttpException extends RuntimeException {
    private final MessageCd messageCd;
    private final HttpStatus httpStatus;

    public HttpException(MessageCd messageCd, HttpStatus httpStatus) {
        this.messageCd = messageCd;
        this.httpStatus = httpStatus;
    }

    public HttpException(String message, MessageCd messageCd, HttpStatus httpStatus) {
        super(message);
        this.messageCd = messageCd;
        this.httpStatus = httpStatus;
    }
}
