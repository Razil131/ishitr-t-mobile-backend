package ru.tmobile.backend.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public class BusinessRuleViolationException extends RuntimeException {
    private final String errorCode;
    private final HttpStatus httpStatus;

    public BusinessRuleViolationException(String errorCode, String message, HttpStatus httpStatus) {
        super(message);
        this.errorCode = errorCode;
        this.httpStatus = httpStatus;
    }
}
