package ru.tmobile.backend.service;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import ru.tmobile.backend.dto.auth.LogoutRequest;
import ru.tmobile.backend.dto.auth.RefreshRequest;
import ru.tmobile.backend.dto.auth.RefreshResponse;
import ru.tmobile.backend.dto.auth.RequestCodeRequest;
import ru.tmobile.backend.dto.auth.RequestCodeResponse;
import ru.tmobile.backend.dto.auth.TokenPair;
import ru.tmobile.backend.dto.auth.UserProfile;
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

@Service
@AllArgsConstructor 
public class AuthService {
    private static final Logger log = LoggerFactory.getLogger(AuthService.class);
    private static final String SYNTHETIC_CODE = "0000";
    private static final int CODE_TTL_SECONDS = 180;
    private static final int COOLDOWN_SECONDS = 60;
    private static final int MAX_ATTEMPTS = 3;
    private static final int LOCKOUT_SECONDS = 300;
    private static final String DEFAULT_ROLE = "USER";
    private static final long NEW_USER_WINDOW_MINUTES = 5;

    private final UserRepository userRepository;
    private final AuthCodeRepository authCodeRepository;
    private final UserSessionRepository userSessionRepository;
    private final JwtTokenProvider tokenProvider;

    @Transactional
    public RequestCodeResponse requestCode(RequestCodeRequest request) {
        String phone = request.phone_number();
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);

        UserEntity user = userRepository.findByPhoneNumber(phone)
                .orElseGet(() -> {
                    UserEntity newUser = new UserEntity();
                    newUser.setPhoneNumber(phone);
                    newUser.setCreatedAt(now);
                    newUser = userRepository.save(newUser);
                    log.info("Auto-registered new user id={} phone={}", newUser.getId(), phone);
                    return newUser;
                });

        Optional<AuthCodeEntity> lastCode = authCodeRepository
                .findTopByUserIdAndIsUsedFalseOrderByRequestedAtDesc(user.getId());

        if (lastCode.isPresent()) {
            AuthCodeEntity code = lastCode.get();
            if (code.getBlockedUntil() != null && code.getBlockedUntil().isAfter(now)) {
                throw new BusinessRuleViolationException(
                        "MAX_ATTEMPTS_EXCEEDED",
                        "Phone number is blocked due to too many failed attempts",
                        HttpStatus.TOO_MANY_REQUESTS);
            }
        }

        if (lastCode.isPresent()) {
            AuthCodeEntity code = lastCode.get();
            if (code.getRequestedAt() != null) {
                long secondsSinceLastRequest = Duration.between(
                        code.getRequestedAt(), now).getSeconds();
                if (secondsSinceLastRequest < COOLDOWN_SECONDS
                        && code.getExpiresAt().isAfter(now)) {
                    log.debug("Cooldown active, reusing existing code for user={}", user.getId());
                    return new RequestCodeResponse(
                            code.getSessionId(),
                            code.getCode(),
                            CODE_TTL_SECONDS,
                            (int)(COOLDOWN_SECONDS - secondsSinceLastRequest),
                            code.getExpiresAt());
                }
            }
        }

        List<AuthCodeEntity> previousCodes = authCodeRepository
                .findAllByUserIdAndIsUsedFalseAndExpiresAtAfter(user.getId(), now);
        previousCodes.forEach(c -> {
            c.setUsed(true);
            authCodeRepository.save(c);
        });

        UUID sessionId = tokenProvider.generateSessionId();
        OffsetDateTime expiresAt = now.plusSeconds(CODE_TTL_SECONDS);

        AuthCodeEntity newCode = new AuthCodeEntity();
        newCode.setUserId(user.getId());
        newCode.setSessionId(sessionId);
        newCode.setCode(SYNTHETIC_CODE);
        newCode.setExpiresAt(expiresAt);
        newCode.setUsed(false);
        newCode.setAttemptsCount(0);
        newCode.setRequestedAt(now);
        authCodeRepository.save(newCode);

        log.info("Generated auth code for user={}, sessionId={}", user.getId(), sessionId);

        return new RequestCodeResponse(
                sessionId,
                SYNTHETIC_CODE,
                CODE_TTL_SECONDS,
                COOLDOWN_SECONDS,
                expiresAt);
    }

    @Transactional
    public VerifyCodeResponse verifyCode(VerifyCodeRequest request) {
        UUID sessionId = request.session_id();
        String code = request.code();
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);

        AuthCodeEntity authCode = authCodeRepository.findBySessionId(sessionId)
                .orElseThrow(() -> new BusinessRuleViolationException(
                        "INVALID_SESSION",
                        "Session not found",
                        HttpStatus.BAD_REQUEST));

        if (authCode.isUsed()) {
            throw new BusinessRuleViolationException(
                    "INVALID_SESSION",
                    "Code already used",
                    HttpStatus.BAD_REQUEST);
        }

        if (authCode.getBlockedUntil() != null && authCode.getBlockedUntil().isAfter(now)) {
            throw new BusinessRuleViolationException(
                    "MAX_ATTEMPTS_EXCEEDED",
                    "Phone number is blocked due to too many failed attempts",
                    HttpStatus.TOO_MANY_REQUESTS);
        }

        if (authCode.getExpiresAt().isBefore(now)) {
            throw new BusinessRuleViolationException(
                    "CODE_EXPIRED",
                    "Verification code has expired",
                    HttpStatus.BAD_REQUEST);
        }

        if (!authCode.getCode().equals(code)) {
            authCode.setAttemptsCount(authCode.getAttemptsCount() + 1);

            if (authCode.getAttemptsCount() >= MAX_ATTEMPTS) {
                authCode.setBlockedUntil(now.plusSeconds(LOCKOUT_SECONDS));
                authCodeRepository.save(authCode);
                throw new BusinessRuleViolationException(
                        "MAX_ATTEMPTS_EXCEEDED",
                        "Too many failed attempts. Phone number blocked for 5 minutes",
                        HttpStatus.TOO_MANY_REQUESTS);
            }

            authCodeRepository.save(authCode);
            throw new BusinessRuleViolationException(
                    "INVALID_CODE",
                    "Invalid verification code",
                    HttpStatus.BAD_REQUEST);
        }

        authCode.setUsed(true);
        authCodeRepository.save(authCode);

        UserEntity user = userRepository.findById(authCode.getUserId())
                .orElseThrow(() -> new BusinessRuleViolationException(
                        "USER_NOT_FOUND", "User not found", HttpStatus.INTERNAL_SERVER_ERROR));

        boolean isNewUser = Duration.between(user.getCreatedAt(), now).toMinutes() < NEW_USER_WINDOW_MINUTES;

        TokenPair tokens = generateTokenPair(user.getId());

        UserSessionEntity session = new UserSessionEntity();
        session.setUserId(user.getId());
        session.setAccessToken(tokens.access_token());
        session.setRefreshToken(tokens.refresh_token());
        session.setExpiresAt(tokens.refresh_token_expires_at());
        userSessionRepository.save(session);

        UserProfile profile = new UserProfile(
                user.getId(),
                user.getPhoneNumber(),
                user.getName(),
                user.getCreatedAt());

        log.info("User id={} verified successfully, sessionId={}", user.getId(), sessionId);

        return new VerifyCodeResponse(tokens, profile, isNewUser, null);
    }

    @Transactional
    public RefreshResponse refresh(RefreshRequest request) {
        String refreshToken = request.refresh_token();

        UserSessionEntity oldSession = userSessionRepository
                .findByRefreshTokenAndRevokedAtIsNull(refreshToken)
                .orElseThrow(() -> new BusinessRuleViolationException(
                        "INVALID_REFRESH_TOKEN",
                        "Refresh token is invalid, expired or revoked",
                        HttpStatus.UNAUTHORIZED));

        if (oldSession.getExpiresAt().isBefore(OffsetDateTime.now(ZoneOffset.UTC))) {
            oldSession.setRevokedAt(OffsetDateTime.now(ZoneOffset.UTC));
            userSessionRepository.save(oldSession);
            throw new BusinessRuleViolationException(
                    "INVALID_REFRESH_TOKEN",
                    "Refresh token has expired",
                    HttpStatus.UNAUTHORIZED);
        }

        oldSession.setRevokedAt(OffsetDateTime.now(ZoneOffset.UTC));
        userSessionRepository.save(oldSession);

        TokenPair tokens = generateTokenPair(oldSession.getUserId());

        UserSessionEntity newSession = new UserSessionEntity();
        newSession.setUserId(oldSession.getUserId());
        newSession.setAccessToken(tokens.access_token());
        newSession.setRefreshToken(tokens.refresh_token());
        newSession.setExpiresAt(tokens.refresh_token_expires_at());
        userSessionRepository.save(newSession);

        log.info("Rotated session for user={}", oldSession.getUserId());
        return new RefreshResponse(tokens);
    }

    @Transactional
    public void logout(LogoutRequest request) {
        String refreshToken = request.refresh_token();
        Optional<UserSessionEntity> session = userSessionRepository
                .findByRefreshTokenAndRevokedAtIsNull(refreshToken);

        session.ifPresent(s -> {
            s.setRevokedAt(OffsetDateTime.now(ZoneOffset.UTC));
            userSessionRepository.save(s);
            log.info("Revoked session for user={}", s.getUserId());
        });
    }

    private TokenPair generateTokenPair(Long userId) {
        String access = tokenProvider.generateAccessToken(userId, DEFAULT_ROLE);
        String refresh = tokenProvider.generateRefreshToken();

        return new TokenPair(
                access,
                refresh,
                tokenProvider.getAccessTokenTtlSeconds(),
                tokenProvider.getRefreshTokenTtlSeconds(),
                tokenProvider.accessTokenExpiresAt(),
                tokenProvider.refreshTokenExpiresAt()
        );
    }

}