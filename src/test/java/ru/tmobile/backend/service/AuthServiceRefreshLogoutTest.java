package ru.tmobile.backend.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import ru.tmobile.backend.dto.auth.LogoutRequest;
import ru.tmobile.backend.dto.auth.RefreshRequest;
import ru.tmobile.backend.dto.auth.RefreshResponse;
import ru.tmobile.backend.entity.UserSessionEntity;
import ru.tmobile.backend.exception.BusinessRuleViolationException;
import ru.tmobile.backend.repository.AuthCodeRepository;
import ru.tmobile.backend.repository.UserRepository;
import ru.tmobile.backend.repository.UserSessionRepository;
import ru.tmobile.backend.security.JwtTokenProvider;

@ExtendWith(MockitoExtension.class)
class AuthServiceRefreshLogoutTest {
    @Mock UserRepository users;
    @Mock AuthCodeRepository codes;
    @Mock UserSessionRepository sessions;
    @Mock JwtTokenProvider jwt;
    AuthService svc;
    OffsetDateTime now;

    @BeforeEach void up() {
        svc = new AuthService(users, codes, sessions, jwt);
        now = OffsetDateTime.now(ZoneOffset.UTC);
    }

    void setupJwtStubs() {
        when(jwt.generateAccessToken(anyLong(), eq("USER"))).thenReturn("new-access");
        when(jwt.generateRefreshToken()).thenReturn("new-refresh");
        when(jwt.getAccessTokenTtlSeconds()).thenReturn(900);
        when(jwt.getRefreshTokenTtlSeconds()).thenReturn(2592000);
        when(jwt.accessTokenExpiresAt()).thenReturn(now.plusMinutes(15));
        when(jwt.refreshTokenExpiresAt()).thenReturn(now.plusDays(30));
    }

    UserSessionEntity sess(long uid, String ref, OffsetDateTime exp) {
        UserSessionEntity e = new UserSessionEntity(); e.setId(10L); e.setUserId(uid); e.setRefreshToken(ref); e.setAccessToken("a"); e.setExpiresAt(exp); return e;
    }

    @Test void refreshRejectsUnknownToken() {
        when(sessions.findByRefreshTokenAndRevokedAtIsNull("ghost")).thenReturn(Optional.empty());
        BusinessRuleViolationException ex = assertThrows(BusinessRuleViolationException.class,
            () -> svc.refresh(new RefreshRequest("ghost")));
        assertEquals("INVALID_REFRESH_TOKEN", ex.getErrorCode());
        assertEquals(HttpStatus.UNAUTHORIZED, ex.getHttpStatus());
    }

    @Test void refreshRevokesExpiredSessionWithoutIssuingTokens() {
        UserSessionEntity expired = sess(7L, "ref-exp", now.minusSeconds(10));
        when(sessions.findByRefreshTokenAndRevokedAtIsNull("ref-exp")).thenReturn(Optional.of(expired));

        BusinessRuleViolationException ex = assertThrows(BusinessRuleViolationException.class,
            () -> svc.refresh(new RefreshRequest("ref-exp")));
        assertEquals("INVALID_REFRESH_TOKEN", ex.getErrorCode());
        assertNotNull(expired.getRevokedAt());
        verify(jwt, never()).generateAccessToken(anyLong(), anyString());
    }

    @Test void refreshRotatesSessionAndReturnsNewTokens() {
        setupJwtStubs();
        UserSessionEntity active = sess(7L, "ref-ok", now.plusDays(5));
        when(sessions.findByRefreshTokenAndRevokedAtIsNull("ref-ok")).thenReturn(Optional.of(active));

        RefreshResponse r = svc.refresh(new RefreshRequest("ref-ok"));

        assertNotNull(active.getRevokedAt());
        assertEquals("new-access", r.tokens().access_token());
        assertEquals("new-refresh", r.tokens().refresh_token());
        ArgumentCaptor<UserSessionEntity> cap = ArgumentCaptor.forClass(UserSessionEntity.class);
        verify(sessions, atLeast(1)).save(cap.capture());
        assertTrue(cap.getAllValues().stream().anyMatch(s ->
            "new-refresh".equals(s.getRefreshToken()) && s.getUserId() == 7L && s.getRevokedAt() == null));
    }

    @Test void refreshCannotBeReusedTwice() {
        setupJwtStubs();
        UserSessionEntity active = sess(7L, "ref-once", now.plusDays(5));
        when(sessions.findByRefreshTokenAndRevokedAtIsNull("ref-once"))
            .thenReturn(Optional.of(active))
            .thenReturn(Optional.empty());
        svc.refresh(new RefreshRequest("ref-once"));
        BusinessRuleViolationException ex = assertThrows(BusinessRuleViolationException.class,
            () -> svc.refresh(new RefreshRequest("ref-once")));
        assertEquals("INVALID_REFRESH_TOKEN", ex.getErrorCode());
    }

    @Test void logoutRevokesActiveSession() {
        UserSessionEntity active = sess(4L, "ref-logout", now.plusDays(5));
        when(sessions.findByRefreshTokenAndRevokedAtIsNull("ref-logout")).thenReturn(Optional.of(active));

        svc.logout(new LogoutRequest("ref-logout"));

        assertNotNull(active.getRevokedAt());
        verify(sessions).save(active);
    }

    @Test void logoutIsIdempotentForUnknownToken() {
        when(sessions.findByRefreshTokenAndRevokedAtIsNull("ghost")).thenReturn(Optional.empty());
        assertDoesNotThrow(() -> svc.logout(new LogoutRequest("ghost")));
        verify(sessions, never()).save(any());
    }
}
