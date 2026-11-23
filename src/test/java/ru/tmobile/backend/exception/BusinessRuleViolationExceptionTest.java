package ru.tmobile.backend.exception;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class BusinessRuleViolationExceptionTest {

	@Test
	void exceptionKeepsCodeMessageAndStatus() {
		BusinessRuleViolationException ex = new BusinessRuleViolationException(
				"INVALID_CODE", "Invalid verification code", HttpStatus.BAD_REQUEST);

		assertEquals("INVALID_CODE", ex.getErrorCode());
		assertEquals("Invalid verification code", ex.getMessage());
		assertEquals(HttpStatus.BAD_REQUEST, ex.getHttpStatus());
		assertTrue(ex instanceof RuntimeException);
	}
}
