package org.imprime.ai.api.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.imprime.ai.api.http.ServiceContext;
import org.imprime.ai.api.http.response.BaseResponse;
import org.imprime.ai.api.model.enums.MessageCd;
import org.imprime.ai.api.model.exception.HttpException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@Slf4j
@RestControllerAdvice
@RequiredArgsConstructor
public class ErrorHandler {

    @ExceptionHandler(value = { HttpException.class })
    public ResponseEntity<?> handleException(HttpException e) {
        ServiceContext.getContext().addMessage(e.getMessageCd(), e.getArgs());
        ServiceContext.addException(e);

        return BaseResponse.build(null, e.getHttpStatus());
    }

    @ExceptionHandler(value = { NoResourceFoundException.class })
    public ResponseEntity<?> handleException(NoResourceFoundException e) {
        ServiceContext.getContext().addMessage(MessageCd.GENERIC_404);
        ServiceContext.addException(e);

        return BaseResponse.build(null, HttpStatus.NOT_FOUND);
    }

    @ExceptionHandler(value = { Exception.class })
    public ResponseEntity<?> handleException(Exception e) {
        ServiceContext.getContext().addMessage(MessageCd.INTERNAL_SERVER_ERROR);
        ServiceContext.addException(e);

        return BaseResponse.build(null, HttpStatus.INTERNAL_SERVER_ERROR);
    }
}
