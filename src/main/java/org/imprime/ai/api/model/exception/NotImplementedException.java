package org.imprime.ai.api.model.exception;

import org.imprime.ai.api.model.enums.MessageCd;
import org.springframework.http.HttpStatus;

public class NotImplementedException extends HttpException {
    private static final HttpStatus httpStatus = HttpStatus.NOT_IMPLEMENTED;
    private static final MessageCd messageCd = MessageCd.NOT_IMPLEMENTED;

    public NotImplementedException() {
        super("Endpoint ainda não implementado", messageCd, httpStatus, new String[0]);
    }

    public NotImplementedException(String detail) {
        super(detail, messageCd, httpStatus, new String[]{detail});
    }
}
