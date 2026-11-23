package ru.tmobile.backend.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import ru.tmobile.backend.dto.auth.VerifyCodeRequest;
import ru.tmobile.backend.dto.auth.VerifyCodeResponse;
import ru.tmobile.backend.entity.AuthCodeEntity;
import ru.tmobile.backend.entity.UserEntity;
import ru.tmobile.backend.entity.UserSessionEntity;
import ru.tmobile.backend.exception.BusinessRuleViolationException;
import ru.tmobile.backend.repository.AuthCodeRepository;
import ru.tmobile.backend.repository.UserRepository;
import ru.tmobile.backend.repository.UserSessionRepository;
import ru.tmobile.backend.security.JwtTokenProvider;

@ExtendWith(MockitoExtension.class)
class AuthServiceVerifyCodeTest {
    static final String PHONE = "+79991234567";
    static final UUID SID = UUID.fromString("11111111-1111-1111-1111-111111111111");

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
        when(jwt.generateAccessToken(anyLong(), eq("USER"))).thenReturn("access");
        when(jwt.generateRefreshToken()).thenReturn("refresh");
        when(jwt.getAccessTokenTtlSeconds()).thenReturn(900);
        when(jwt.getRefreshTokenTtlSeconds()).thenReturn(2592000);
        when(jwt.accessTokenExpiresAt()).thenReturn(now.plusMinutes(15));
        when(jwt.refreshTokenExpiresAt()).thenReturn(now.plusDays(30));
    }
    AuthCodeEntity c(UUID s, long uid, OffsetDateTime exp, int att) {
        AuthCodeEntity e = new AuthCodeEntity(); e.setSessionId(s); e.setUserId(uid); e.setCode("0000"); e.setExpiresAt(exp); e.setAttemptsCount(att); e.setUsed(false); return e;
    }
    UserEntity u(long id, OffsetDateTime cr) {
        UserEntity e = new UserEntity(); e.setId(id); e.setPhoneNumber(PHONE); e.setName("Ivan"); e.setCreatedAt(cr); return e;
    }

    @Test void rejectsUnknownSession() {
        when(codes.findBySessionId(SID)).thenReturn(Optional.empty());
        BusinessRuleViolationException ex = assertThrows(BusinessRuleViolationException.class,
            () -> svc.verifyCode(new VerifyCodeRequest(SID, "0000")));
        assertEquals("INVALID_SESSION", ex.getErrorCode());
        assertEquals(HttpStatus.BAD_REQUEST, ex.getHttpStatus());
    }

    @Test void rejectsAlreadyUsedCode() {
        AuthCodeEntity used = c(SID, 1L, now.plusSeconds(60), 0);
        used.setUsed(true);
        when(codes.findBySessionId(SID)).thenReturn(Optional.of(used));
        BusinessRuleViolationException ex = assertThrows(BusinessRuleViolationException.class,
            () -> svc.verifyCode(new VerifyCodeRequest(SID, "0000")));
        assertEquals("INVALID_SESSION", ex.getErrorCode());
    }

    @Test void rejectsWhenBlockedUntilFuture() {
        AuthCodeEntity locked = c(SID, 1L, now.plusSeconds(60), 3);
        locked.setBlockedUntil(now.plusSeconds(60));
        when(codes.findBySessionId(SID)).thenReturn(Optional.of(locked));
        BusinessRuleViolationException ex = assertThrows(BusinessRuleViolationException.class,
            () -> svc.verifyCode(new VerifyCodeRequest(SID, "0000")));
        assertEquals("MAX_ATTEMPTS_EXCEEDED", ex.getErrorCode());
        assertEquals(HttpStatus.TOO_MANY_REQUESTS, ex.getHttpStatus());
    }

    @Test void rejectsExpiredCode() {
        AuthCodeEntity expired = c(SID, 1L, now.minusSeconds(10), 0);
        when(codes.findBySessionId(SID)).thenReturn(Optional.of(expired));
        BusinessRuleViolationException ex = assertThrows(BusinessRuleViolationException.class,
            () -> svc.verifyCode(new VerifyCodeRequest(SID, "0000")));
        assertEquals("CODE_EXPIRED", ex.getErrorCode());
    }

    @Test void incrementsAttemptsForWrongCode() {
        AuthCodeEntity wrong = c(SID, 1L, now.plusSeconds(60), 1);
        when(codes.findBySessionId(SID)).thenReturn(Optional.of(wrong));
        BusinessRuleViolationException ex = assertThrows(BusinessRuleViolationException.class,
            () -> svc.verifyCode(new VerifyCodeRequest(SID, "9999")));
        assertEquals("INVALID_CODE", ex.getErrorCode());
        assertEquals(2, wrong.getAttemptsCount());
        assertNull(wrong.getBlockedUntil());
        verify(codes).save(wrong);
    }

    @Test void locksAfterThirdFailedAttempt() {
        AuthCodeEntity thirdFail = c(SID, 1L, now.plusSeconds(60), 2);
        when(codes.findBySessionId(SID)).thenReturn(Optional.of(thirdFail));
        BusinessRuleViolationException ex = assertThrows(BusinessRuleViolationException.class,
            () -> svc.verifyCode(new VerifyCodeRequest(SID, "9999")));
        assertEquals("MAX_ATTEMPTS_EXCEEDED", ex.getErrorCode());
        assertEquals(HttpStatus.TOO_MANY_REQUESTS, ex.getHttpStatus());
        assertEquals(3, thirdFail.getAttemptsCount());
        assertNotNull(thirdFail.getBlockedUntil());
        assertTrue(thirdFail.getBlockedUntil().isAfter(now.plusSeconds(299)));
    }

    @Test void issuesTokensAndMarksCodeUsedForNewUser() {
        setupJwtStubs();
        AuthCodeEntity fresh = c(SID, 1L, now.plusSeconds(60), 0);
        UserEntity newUser = u(1L, now.minusMinutes(1));
        when(codes.findBySessionId(SID)).thenReturn(Optional.of(fresh));
        when(users.findById(1L)).thenReturn(Optional.of(newUser));

        VerifyCodeResponse r = svc.verifyCode(new VerifyCodeRequest(SID, "0000"));

        assertTrue(fresh.isUsed());
        assertTrue(r.is_new_user());
        assertEquals("access", r.tokens().access_token());
        assertEquals("refresh", r.tokens().refresh_token());
        assertEquals(1L, r.user().user_id());
        assertNull(r.group());
        ArgumentCaptor<UserSessionEntity> cap = ArgumentCaptor.forClass(UserSessionEntity.class);
        verify(sessions).save(cap.capture());
        assertEquals(1L, cap.getValue().getUserId());
        assertEquals("access", cap.getValue().getAccessToken());
        assertEquals("refresh", cap.getValue().getRefreshToken());
    }

    @Test void marksOldUserAsNotNew() {
        setupJwtStubs();
        AuthCodeEntity fresh = c(SID, 2L, now.plusSeconds(60), 0);
        UserEntity old = u(2L, now.minusMinutes(30));
        when(codes.findBySessionId(SID)).thenReturn(Optional.of(fresh));
        when(users.findById(2L)).thenReturn(Optional.of(old));

        VerifyCodeResponse r = svc.verifyCode(new VerifyCodeRequest(SID, "0000"));
        assertFalse(r.is_new_user());
    }

    @Test void failsWhenUserMissingAfterValidCode() {
        AuthCodeEntity fresh = c(SID, 99L, now.plusSeconds(60), 0);
        when(codes.findBySessionId(SID)).thenReturn(Optional.of(fresh));
        when(users.findById(99L)).thenReturn(Optional.empty());

        BusinessRuleViolationException ex = assertThrows(BusinessRuleViolationException.class,
            () -> svc.verifyCode(new VerifyCodeRequest(SID, "0000")));
        assertEquals("USER_NOT_FOUND", ex.getErrorCode());
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, ex.getHttpStatus());
        assertTrue(fresh.isUsed());
    }

    @Test void boundaryNewUserExactlyFiveMinutesOld() {
        setupJwtStubs();
        AuthCodeEntity fresh = c(SID, 3L, now.plusSeconds(60), 0);
        UserEntity boundary = u(3L, now.minusMinutes(5));
        when(codes.findBySessionId(SID)).thenReturn(Optional.of(fresh));
        when(users.findById(3L)).thenReturn(Optional.of(boundary));

        VerifyCodeResponse r = svc.verifyCode(new VerifyCodeRequest(SID, "0000"));
        assertFalse(r.is_new_user());
    }
}
