package ru.tmobile.backend.exception;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import static org.junit.jupiter.api.Assertions.*;

class AuthorizationExceptionTest {

    @Test
    void constructorSetsFieldsCorrectly() {
        AuthorizationException ex = new AuthorizationException("FORBIDDEN", "Only owner can perform this action");

        assertEquals("FORBIDDEN", ex.getErrorCode());
        assertEquals("Only owner can perform this action", ex.getMessage());
        assertEquals(HttpStatus.FORBIDDEN, ex.getHttpStatus());
    }

    @Test
    void httpStatusIsAlways403() {
        AuthorizationException ex = new AuthorizationException("ANY_CODE", "Any message");
        assertEquals(HttpStatus.FORBIDDEN, ex.getHttpStatus());
    }
}
