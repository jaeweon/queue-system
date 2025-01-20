package com.queuesystem.queuesystem.exception;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import reactor.core.publisher.Mono;

@RestControllerAdvice
public class ApplicationAdvice {

    @ExceptionHandler(ApplicationException.class)
    Mono<?> applicationException(ApplicationException e) {
       return Mono.just(ResponseEntity
               .status(e.getHttpStatus())
               .body(new ServiceExceptionResponse(e.getCode(), e.getMessage())));
    }

    public record ServiceExceptionResponse(String code, String message) {

    }
}
