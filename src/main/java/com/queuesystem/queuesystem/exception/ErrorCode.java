package com.queuesystem.queuesystem.exception;

import lombok.AllArgsConstructor;
import org.springframework.http.HttpStatus;

@AllArgsConstructor
public enum ErrorCode {
    QUEUE_ALREADY_REGISTER_USER(HttpStatus.CONFLICT, "UQ-0001", "Already register user.");

    private HttpStatus httpStatus;
    private String message;
    private String code;

    public ApplicationException build() {
        return new ApplicationException(httpStatus, message, code);
    }

    public ApplicationException build(Object... args) {
        return new ApplicationException(httpStatus, message.formatted(args), code);
    }
}
