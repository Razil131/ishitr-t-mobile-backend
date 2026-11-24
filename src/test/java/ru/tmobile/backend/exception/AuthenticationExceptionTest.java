package ru.tmobile.backend.exception;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import static org.junit.jupiter.api.Assertions.*;

class AuthenticationExceptionTest {

    @Test
    void constructorSetsFieldsCorrectly() {
        AuthenticationException ex = new AuthenticationException("UNAUTHORIZED", "Invalid JWT token");

        assertEquals("UNAUTHORIZED", ex.getErrorCode());
        assertEquals("Invalid JWT token", ex.getMessage());
        assertEquals(HttpStatus.UNAUTHORIZED, ex.getHttpStatus());
    }

    @Test
    void httpStatusIsAlways401() {
        AuthenticationException ex = new AuthenticationException("ANY_CODE", "Any message");
        assertEquals(HttpStatus.UNAUTHORIZED, ex.getHttpStatus());
    }
}
