package ru.tmobile.backend.exception;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;

import ru.tmobile.backend.dto.ErrorResponse;
import ru.tmobile.backend.dto.auth.RequestCodeRequest;

class GlobalExceptionHandlerTest {
    GlobalExceptionHandler handler;

    @BeforeEach void up() { handler = new GlobalExceptionHandler(); }

    @Test void handlesBusinessRuleWithStatusAndCode() {
        BusinessRuleViolationException ex = new BusinessRuleViolationException(
            "INVALID_CODE", "bad code", HttpStatus.BAD_REQUEST);
        ResponseEntity<ErrorResponse> r = handler.handleBusinessRule(ex);
        assertEquals(HttpStatus.BAD_REQUEST, r.getStatusCode());
        assertNotNull(r.getBody());
        assertEquals("INVALID_CODE", r.getBody().error().code());
        assertEquals("bad code", r.getBody().error().message());
        assertNull(r.getBody().error().details());
    }

    @Test void handlesBusinessRuleWithConflictStatus() {
        BusinessRuleViolationException ex = new BusinessRuleViolationException(
            "BR_01", "already in group", HttpStatus.CONFLICT);
        ResponseEntity<ErrorResponse> r = handler.handleBusinessRule(ex);
        assertEquals(HttpStatus.CONFLICT, r.getStatusCode());
    }

    @Test void handleAuthenticationReturns401() {
        AuthenticationException ex = new AuthenticationException(
            "UNAUTHORIZED", "Invalid or missing JWT token");

        ResponseEntity<ErrorResponse> response = handler.handleAuthentication(ex);

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("UNAUTHORIZED", response.getBody().error().code());
        assertEquals("Invalid or missing JWT token", response.getBody().error().message());
    }

    @Test void handleAuthorizationReturns403() {
        AuthorizationException ex = new AuthorizationException(
            "FORBIDDEN", "Only group owner can perform this action");

        ResponseEntity<ErrorResponse> response = handler.handleAuthorization(ex);

        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("FORBIDDEN", response.getBody().error().code());
        assertEquals("Only group owner can perform this action", response.getBody().error().message());
    }

    @Test void handlesValidationErrorsAggregatingFieldMessages() {
        RequestCodeRequest target = new RequestCodeRequest("");
        BeanPropertyBindingResult br = new BeanPropertyBindingResult(target, "request");
        br.addError(new FieldError("request", "phone_number", "must not be blank"));
        MethodArgumentNotValidException ex = new MethodArgumentNotValidException(null, br);

        ResponseEntity<ErrorResponse> r = handler.handleValidation(ex);
        assertEquals(HttpStatus.BAD_REQUEST, r.getStatusCode());
        assertEquals("VALIDATION_ERROR", r.getBody().error().code());
        assertEquals("must not be blank", r.getBody().error().details().get("phone_number"));
    }

    @Test void validationHandlerDoesNotFailWithEmptyErrors() {
        RequestCodeRequest target = new RequestCodeRequest("");
        BeanPropertyBindingResult br = new BeanPropertyBindingResult(target, "request");
        MethodArgumentNotValidException ex = new MethodArgumentNotValidException(null, br);
        ResponseEntity<ErrorResponse> r = handler.handleValidation(ex);
        assertEquals(HttpStatus.BAD_REQUEST, r.getStatusCode());
        assertTrue(r.getBody().error().details().isEmpty());
    }
}
