package org.imprime.ai.api.controller;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.imprime.ai.api.http.ServiceContext;
import org.imprime.ai.api.http.response.BaseResponse;
import org.imprime.ai.api.http.response.ResponseMessage;
import org.imprime.ai.api.model.MessageLkup;
import org.imprime.ai.api.model.enums.MessageCd;
import org.imprime.ai.api.model.exception.HttpException;
import org.imprime.ai.api.model.exception.NotImplementedException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.validation.ObjectError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@Slf4j
@RestControllerAdvice
@RequiredArgsConstructor
public class ErrorHandler {

    @ExceptionHandler(value = { NotImplementedException.class })
    public ResponseEntity<BaseResponse<Void>> handleNotImplemented(NotImplementedException e) {
        ServiceContext ctx = ServiceContext.getContext();
        ctx.addMessage(e.getMessageCd(), e.getArgs());
        if (ctx.getMessages().isEmpty()) {
            ctx.addResponseMessage(new ResponseMessage(
                    e.getMessageCd().getCode(),
                    e.getMessage() != null ? e.getMessage() : "Funcionalidade ainda não implementada",
                    MessageLkup.MessageType.ERROR
            ));
        } else {
            ResponseMessage last = ctx.getMessages().getLast();
            if (last.getMessage() == null || last.getMessage().isBlank()) {
                last.setMessage(e.getMessage() != null ? e.getMessage() : "Funcionalidade ainda não implementada");
            }
        }
        ServiceContext.addException(e);
        return BaseResponse.build(null, e.getHttpStatus());
    }

    @ExceptionHandler(value = { HttpException.class })
    public ResponseEntity<BaseResponse<Void>> handleException(HttpException e) {
        ServiceContext.getContext().addMessage(e.getMessageCd(), e.getArgs());
        ServiceContext.addException(e);

        return BaseResponse.build(null, e.getHttpStatus());
    }

    @ExceptionHandler(value = { MethodArgumentNotValidException.class })
    public ResponseEntity<BaseResponse<Void>> handleMethodArgumentNotValid(MethodArgumentNotValidException e) {
        ServiceContext ctx = ServiceContext.getContext();
        for (FieldError fieldError : e.getBindingResult().getFieldErrors()) {
            ctx.addResponseMessage(new ResponseMessage(
                    MessageCd.VALIDATION_ERROR.getCode(),
                    fieldError.getField() + ": " + fieldError.getDefaultMessage(),
                    MessageLkup.MessageType.ERROR
            ));
        }
        for (ObjectError globalError : e.getBindingResult().getGlobalErrors()) {
            ctx.addResponseMessage(new ResponseMessage(
                    MessageCd.VALIDATION_ERROR.getCode(),
                    globalError.getObjectName() + ": " + globalError.getDefaultMessage(),
                    MessageLkup.MessageType.ERROR
            ));
        }
        ServiceContext.addException(e);
        return BaseResponse.build(null, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(value = { ConstraintViolationException.class })
    public ResponseEntity<BaseResponse<Void>> handleConstraintViolation(ConstraintViolationException e) {
        ServiceContext ctx = ServiceContext.getContext();
        for (ConstraintViolation<?> violation : e.getConstraintViolations()) {
            ctx.addResponseMessage(new ResponseMessage(
                    MessageCd.VALIDATION_ERROR.getCode(),
                    violation.getPropertyPath() + ": " + violation.getMessage(),
                    MessageLkup.MessageType.ERROR
            ));
        }
        ServiceContext.addException(e);
        return BaseResponse.build(null, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(value = { HttpMessageNotReadableException.class })
    public ResponseEntity<BaseResponse<Void>> handleUnreadableMessage(HttpMessageNotReadableException e) {
        ServiceContext ctx = ServiceContext.getContext();
        ctx.addMessage(MessageCd.INVALID_REQUEST_BODY);
        if (ctx.getMessages().isEmpty()) {
            ctx.addResponseMessage(new ResponseMessage(
                    MessageCd.INVALID_REQUEST_BODY.getCode(),
                    "Corpo da requisição JSON inválido ou malformado",
                    MessageLkup.MessageType.ERROR
            ));
        } else {
            ResponseMessage last = ctx.getMessages().getLast();
            if (last.getMessage() == null || last.getMessage().isBlank()) {
                last.setMessage("Corpo da requisição JSON inválido ou malformado");
            }
        }
        ServiceContext.addException(e);
        return BaseResponse.build(null, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(value = { MethodArgumentTypeMismatchException.class })
    public ResponseEntity<BaseResponse<Void>> handleTypeMismatch(MethodArgumentTypeMismatchException e) {
        ServiceContext.getContext().addResponseMessage(new ResponseMessage(
                MessageCd.VALIDATION_ERROR.getCode(),
                "Valor inválido para o parâmetro '" + e.getName() + "'",
                MessageLkup.MessageType.ERROR
        ));
        ServiceContext.addException(e);
        return BaseResponse.build(null, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(value = { MissingServletRequestParameterException.class })
    public ResponseEntity<BaseResponse<Void>> handleMissingParam(MissingServletRequestParameterException e) {
        ServiceContext.getContext().addResponseMessage(new ResponseMessage(
                MessageCd.VALIDATION_ERROR.getCode(),
                "Parâmetro obrigatório ausente: " + e.getParameterName(),
                MessageLkup.MessageType.ERROR
        ));
        ServiceContext.addException(e);
        return BaseResponse.build(null, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(value = { NoResourceFoundException.class })
    public ResponseEntity<BaseResponse<Void>> handleException(NoResourceFoundException e) {
        ServiceContext.getContext().addMessage(MessageCd.GENERIC_404);
        ServiceContext.addException(e);

        return BaseResponse.build(null, HttpStatus.NOT_FOUND);
    }

    @ExceptionHandler(value = { Exception.class })
    public ResponseEntity<BaseResponse<Void>> handleException(Exception e) {
        ServiceContext.getContext().addMessage(MessageCd.INTERNAL_SERVER_ERROR);
        ServiceContext.addException(e);

        log.error("Unhandled exception: ", e);

        return BaseResponse.build(null, HttpStatus.INTERNAL_SERVER_ERROR);
    }
}
