package com.queuesystem.queuesystem.exception;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;
@AllArgsConstructor
@Getter
public enum ErrorCode {
    QUEUE_ALREADY_REGISTER_USER(HttpStatus.CONFLICT, "UQ-0001", "Already register user."),
    QUEUE_REGISTRATION_FAILED(HttpStatus.CONFLICT, "UQ-0002", "Queue registration failed."),
    USER_NOT_FOUND_IN_QUEUE(HttpStatus.CONFLICT, "UQ-0002", "User not found in queue.");

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;

    public ApplicationException build() {
        return new ApplicationException(httpStatus, message, code);
    }

    public ApplicationException build(Object... args) {
        return new ApplicationException(httpStatus, String.format(message, args), code);
    }
}