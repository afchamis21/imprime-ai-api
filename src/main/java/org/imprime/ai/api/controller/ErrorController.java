package org.imprime.ai.api.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.imprime.ai.api.http.ServiceContext;
import org.imprime.ai.api.http.response.BaseResponse;
import org.imprime.ai.api.model.MessageLkup;
import org.imprime.ai.api.model.enums.MessageCd;
import org.imprime.ai.api.model.exception.HttpException;
import org.imprime.ai.api.service.MessageLkupService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.List;

@Slf4j
@RestControllerAdvice
@RequiredArgsConstructor
public class ErrorController {
    private final MessageLkupService messageLkupService;

    @ExceptionHandler(value = { HttpException.class })
    public ResponseEntity<?> handleException(HttpException e) {
        MessageLkup messageLkup = messageLkupService.getMessageByCode(e.getMessageCd());

        return BaseResponse.build(null, e.getHttpStatus(), List.of(messageLkup));
    }

    @ExceptionHandler(value = { NoResourceFoundException.class })
    public ResponseEntity<?> handleException(NoResourceFoundException e) {
        ServiceContext.addException(e);
        MessageLkup messageLkup = messageLkupService.getMessageByCode(MessageCd.GENERIC_404);

        return BaseResponse.build(null, HttpStatus.NOT_FOUND, List.of(messageLkup));
    }

    @ExceptionHandler(value = { Exception.class })
    public ResponseEntity<?> handleException(Exception e) {
        ServiceContext.addException(e);
        MessageLkup messageLkup = messageLkupService.getMessageByCode(MessageCd.INTERNAL_SERVER_ERROR);

        return BaseResponse.build(null, HttpStatus.INTERNAL_SERVER_ERROR, List.of(messageLkup));
    }
}
