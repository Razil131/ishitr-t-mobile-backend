package ru.tmobile.backend.exception;

import org.springframework.http.HttpStatus;

// 401
public class AuthenticationException extends RuntimeException {
    private final String errorCode;

    public AuthenticationException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public HttpStatus getHttpStatus() {
        return HttpStatus.UNAUTHORIZED;
    }
}
