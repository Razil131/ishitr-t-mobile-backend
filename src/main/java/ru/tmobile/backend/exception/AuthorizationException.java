package ru.tmobile.backend.exception;

import org.springframework.http.HttpStatus;

// 403
public class AuthorizationException extends RuntimeException {
    private final String errorCode;

    public AuthorizationException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public HttpStatus getHttpStatus() {
        return HttpStatus.FORBIDDEN;
    }
}
