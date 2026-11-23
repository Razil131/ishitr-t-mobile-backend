package ru.tmobile.backend.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import ru.tmobile.backend.dto.auth.RequestCodeRequest;
import ru.tmobile.backend.dto.auth.RequestCodeResponse;
import ru.tmobile.backend.entity.AuthCodeEntity;
import ru.tmobile.backend.entity.UserEntity;
import ru.tmobile.backend.exception.BusinessRuleViolationException;
import ru.tmobile.backend.repository.AuthCodeRepository;
import ru.tmobile.backend.repository.UserRepository;
import ru.tmobile.backend.repository.UserSessionRepository;
import ru.tmobile.backend.security.JwtTokenProvider;

@ExtendWith(MockitoExtension.class)
class AuthServiceRequestCodeTest {
    static final String PHONE = "+79991234567";
    static final UUID SID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    static final UUID SID2 = UUID.fromString("22222222-2222-2222-2222-222222222222");

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
    UserEntity u(long id, OffsetDateTime cr) {
        UserEntity e = new UserEntity(); e.setId(id); e.setPhoneNumber(PHONE); e.setName("Ivan"); e.setCreatedAt(cr); return e;
    }
    AuthCodeEntity c(UUID s, long uid, OffsetDateTime exp, int att) {
        AuthCodeEntity e = new AuthCodeEntity(); e.setSessionId(s); e.setUserId(uid); e.setCode("0000"); e.setExpiresAt(exp); e.setAttemptsCount(att); e.setUsed(false); return e;
    }

    @Test void reusesActiveCodeInsideCooldown() {
        UserEntity user = u(1L, now.minusMinutes(10));
        AuthCodeEntity last = c(SID, 1L, now.plusSeconds(100), 0);
        last.setRequestedAt(now.minusSeconds(10));
        when(users.findByPhoneNumber(PHONE)).thenReturn(Optional.of(user));
        when(codes.findTopByUserIdAndIsUsedFalseOrderByRequestedAtDesc(1L)).thenReturn(Optional.of(last));
        RequestCodeResponse r = svc.requestCode(new RequestCodeRequest(PHONE));
        assertEquals(SID, r.session_id()); assertEquals("0000", r.code()); assertEquals(180, r.code_ttl_seconds());
        assertTrue(r.cooldown_seconds() >= 48 && r.cooldown_seconds() <= 51);
        assertEquals(last.getExpiresAt(), r.expires_at());
        verify(codes, never()).save(any());
    }

    @Test void rejectsPhoneWhileLocked() {
        UserEntity user = u(1L, now.minusMinutes(10));
        AuthCodeEntity locked = c(SID, 1L, now.plusSeconds(100), 3);
        locked.setBlockedUntil(now.plusSeconds(120));
        when(users.findByPhoneNumber(PHONE)).thenReturn(Optional.of(user));
        when(codes.findTopByUserIdAndIsUsedFalseOrderByRequestedAtDesc(1L)).thenReturn(Optional.of(locked));
        BusinessRuleViolationException ex = assertThrows(BusinessRuleViolationException.class,
            () -> svc.requestCode(new RequestCodeRequest(PHONE)));
        assertEquals("MAX_ATTEMPTS_EXCEEDED", ex.getErrorCode());
        assertEquals(HttpStatus.TOO_MANY_REQUESTS, ex.getHttpStatus());
    }

    @Test void allowsNewCodeAfterLockExpired() {
        UserEntity user = u(1L, now.minusMinutes(10));
        AuthCodeEntity old = c(SID, 1L, now.minusSeconds(1), 3);
        old.setRequestedAt(now.minusSeconds(400)); old.setBlockedUntil(now.minusSeconds(1));
        when(users.findByPhoneNumber(PHONE)).thenReturn(Optional.of(user));
        when(codes.findTopByUserIdAndIsUsedFalseOrderByRequestedAtDesc(1L)).thenReturn(Optional.of(old));
        when(codes.findAllByUserIdAndIsUsedFalseAndExpiresAtAfter(eq(1L), any())).thenReturn(List.of());
        when(jwt.generateSessionId()).thenReturn(SID2);
        RequestCodeResponse r = svc.requestCode(new RequestCodeRequest(PHONE));
        assertEquals(SID2, r.session_id()); assertEquals(60, r.cooldown_seconds());
        ArgumentCaptor<AuthCodeEntity> cap = ArgumentCaptor.forClass(AuthCodeEntity.class);
        verify(codes).save(cap.capture());
        assertEquals(SID2, cap.getValue().getSessionId()); assertEquals(0, cap.getValue().getAttemptsCount());
    }

    @Test void invalidatesPreviousActiveCodes() {
        UserEntity user = u(1L, now.minusMinutes(10));
        AuthCodeEntity last = c(SID, 1L, now.plusSeconds(50), 0);
        last.setRequestedAt(now.minusSeconds(70));
        AuthCodeEntity prev = c(UUID.randomUUID(), 1L, now.plusSeconds(80), 1);
        when(users.findByPhoneNumber(PHONE)).thenReturn(Optional.of(user));
        when(codes.findTopByUserIdAndIsUsedFalseOrderByRequestedAtDesc(1L)).thenReturn(Optional.of(last));
        when(codes.findAllByUserIdAndIsUsedFalseAndExpiresAtAfter(eq(1L), any())).thenReturn(List.of(prev));
        when(jwt.generateSessionId()).thenReturn(SID2);
        svc.requestCode(new RequestCodeRequest(PHONE));
        assertTrue(prev.isUsed());
        ArgumentCaptor<AuthCodeEntity> cap = ArgumentCaptor.forClass(AuthCodeEntity.class);
        verify(codes, times(2)).save(cap.capture());
        assertTrue(cap.getAllValues().stream().anyMatch(x -> SID2.equals(x.getSessionId())));
    }

    @Test void autoRegistersUnknownPhone() {
        when(users.findByPhoneNumber(PHONE)).thenReturn(Optional.empty());
        when(users.save(any(UserEntity.class))).thenAnswer(i -> { UserEntity s = i.getArgument(0); s.setId(9L); return s; });
        when(codes.findTopByUserIdAndIsUsedFalseOrderByRequestedAtDesc(9L)).thenReturn(Optional.empty());
        when(codes.findAllByUserIdAndIsUsedFalseAndExpiresAtAfter(eq(9L), any())).thenReturn(List.of());
        when(jwt.generateSessionId()).thenReturn(SID2);
        svc.requestCode(new RequestCodeRequest(PHONE));
        ArgumentCaptor<UserEntity> uc = ArgumentCaptor.forClass(UserEntity.class);
        verify(users).save(uc.capture()); assertEquals(PHONE, uc.getValue().getPhoneNumber());
        ArgumentCaptor<AuthCodeEntity> cc = ArgumentCaptor.forClass(AuthCodeEntity.class);
        verify(codes).save(cc.capture()); assertEquals(9L, cc.getValue().getUserId());
    }

    @Test void createsFreshCodeWhenLastHasNoRequestedAt() {
        UserEntity user = u(1L, now.minusMinutes(10));
        AuthCodeEntity last = c(SID, 1L, now.plusSeconds(100), 0);
        when(users.findByPhoneNumber(PHONE)).thenReturn(Optional.of(user));
        when(codes.findTopByUserIdAndIsUsedFalseOrderByRequestedAtDesc(1L)).thenReturn(Optional.of(last));
        when(codes.findAllByUserIdAndIsUsedFalseAndExpiresAtAfter(eq(1L), any())).thenReturn(List.of());
        when(jwt.generateSessionId()).thenReturn(SID2);
        RequestCodeResponse r = svc.requestCode(new RequestCodeRequest(PHONE));
        assertEquals(SID2, r.session_id());
        verify(codes).save(any(AuthCodeEntity.class));
    }
}
