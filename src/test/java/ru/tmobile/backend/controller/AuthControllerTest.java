package ru.tmobile.backend.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.fasterxml.jackson.databind.ObjectMapper;

import ru.tmobile.backend.dto.auth.LogoutRequest;
import ru.tmobile.backend.dto.auth.RefreshRequest;
import ru.tmobile.backend.dto.auth.RefreshResponse;
import ru.tmobile.backend.dto.auth.RequestCodeRequest;
import ru.tmobile.backend.dto.auth.RequestCodeResponse;
import ru.tmobile.backend.dto.auth.TokenPair;
import ru.tmobile.backend.dto.auth.VerifyCodeRequest;
import ru.tmobile.backend.dto.auth.VerifyCodeResponse;
import ru.tmobile.backend.dto.auth.UserProfile;
import ru.tmobile.backend.exception.BusinessRuleViolationException;
import ru.tmobile.backend.exception.GlobalExceptionHandler;
import ru.tmobile.backend.service.AuthService;

class AuthControllerTest {
    MockMvc mockMvc;
    AuthService authService;
    ObjectMapper json;

    @BeforeEach void up() {
        authService = mock(AuthService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new AuthController(authService))
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();
        json = new ObjectMapper();
    }

    @Test void requestCodeReturns200WithValidPayload() throws Exception {
        UUID sid = UUID.randomUUID();
        OffsetDateTime exp = OffsetDateTime.now(ZoneOffset.UTC).plusMinutes(3);
        when(authService.requestCode(any(RequestCodeRequest.class)))
            .thenReturn(new RequestCodeResponse(sid, "0000", 180, 60, exp));

        mockMvc.perform(post("/api/v1/auth/request-code")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(new RequestCodeRequest("+79421234567"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.session_id").value(sid.toString()))
            .andExpect(jsonPath("$.code").value("0000"))
            .andExpect(jsonPath("$.code_ttl_seconds").value(180))
            .andExpect(jsonPath("$.cooldown_seconds").value(60));
    }

    @Test void verifyCodeReturns200WithValidSessionAndCode() throws Exception {
        UUID sid = UUID.randomUUID();
        TokenPair tokens = new TokenPair("a", "r", 900, 2592000,
            OffsetDateTime.now(ZoneOffset.UTC).plusMinutes(15),
            OffsetDateTime.now(ZoneOffset.UTC).plusDays(30));
        UserProfile profile = new UserProfile(1L, "+79991234567", "Ivan", OffsetDateTime.now(ZoneOffset.UTC));
        when(authService.verifyCode(any(VerifyCodeRequest.class)))
            .thenReturn(new VerifyCodeResponse(tokens, profile, true, null));

        mockMvc.perform(post("/api/v1/auth/verify-code")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(new VerifyCodeRequest(sid, "0000"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.tokens.access_token").value("a"))
            .andExpect(jsonPath("$.user.user_id").value(1))
            .andExpect(jsonPath("$.is_new_user").value(true));
    }

    @Test void verifyCodeMapsBusinessExceptionToErrorResponse() throws Exception {
        when(authService.verifyCode(any(VerifyCodeRequest.class)))
            .thenThrow(new BusinessRuleViolationException("INVALID_SESSION", "Session not found", HttpStatus.BAD_REQUEST));

        mockMvc.perform(post("/api/v1/auth/verify-code")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(new VerifyCodeRequest(UUID.randomUUID(), "0000"))))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error.code").value("INVALID_SESSION"))
            .andExpect(jsonPath("$.error.message").value("Session not found"));
    }

    @Test void verifyCodeMapsTooManyRequestsStatus() throws Exception {
        when(authService.verifyCode(any(VerifyCodeRequest.class)))
            .thenThrow(new BusinessRuleViolationException("MAX_ATTEMPTS_EXCEEDED", "blocked", HttpStatus.TOO_MANY_REQUESTS));

        mockMvc.perform(post("/api/v1/auth/verify-code")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(new VerifyCodeRequest(UUID.randomUUID(), "0000"))))
            .andExpect(status().isTooManyRequests())
            .andExpect(jsonPath("$.error.code").value("MAX_ATTEMPTS_EXCEEDED"));
    }

    @Test void refreshReturns200WithNewTokenPair() throws Exception {
        TokenPair tokens = new TokenPair("a2", "r2", 900, 2592000,
            OffsetDateTime.now(ZoneOffset.UTC).plusMinutes(15),
            OffsetDateTime.now(ZoneOffset.UTC).plusDays(30));
        when(authService.refresh(any(RefreshRequest.class))).thenReturn(new RefreshResponse(tokens));

        mockMvc.perform(post("/api/v1/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(new RefreshRequest("valid-refresh"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.tokens.access_token").value("a2"));
    }

    @Test void refreshReturns401OnInvalidToken() throws Exception {
        when(authService.refresh(any(RefreshRequest.class)))
            .thenThrow(new BusinessRuleViolationException("INVALID_REFRESH_TOKEN", "bad", HttpStatus.UNAUTHORIZED));

        mockMvc.perform(post("/api/v1/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(new RefreshRequest("bad"))))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.error.code").value("INVALID_REFRESH_TOKEN"));
    }

    @Test void logoutReturns204AndInvokesService() throws Exception {
        mockMvc.perform(post("/api/v1/auth/logout")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(new LogoutRequest("any-refresh"))))
            .andExpect(status().isNoContent());
    }
}
