package ru.tmobile.backend.security;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.Date;
import java.util.UUID;

import javax.crypto.SecretKey;

import org.springframework.stereotype.Component;

import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Claims;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ru.tmobile.backend.config.JwtProperties;

@Component
public class JwtTokenProvider {
    private static final Logger log = LoggerFactory.getLogger(JwtTokenProvider.class);
    private static final int REFRESH_TOKEN_BYTES = 32;

    private final JwtProperties properties;
    private final SecretKey signingKey;
    private final SecureRandom secureRandom = new SecureRandom();

    public JwtTokenProvider(JwtProperties properties) {
        this.properties = properties;
        byte[] keyBytes = properties.getSecret().getBytes(StandardCharsets.UTF_8);
        this.signingKey = Keys.hmacShaKeyFor(keyBytes);
    }

    public String generateAccessToken(Long userId, String role) {
        Instant now = Instant.now();
        Instant expiresAt = now.plus(properties.getAccessTokenTtl());

        return Jwts.builder()
                .subject(userId.toString())
                .claim("user_id", userId)
                .claim("role", role)
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiresAt))
                .signWith(signingKey, Jwts.SIG.HS256)
                .compact();
    }

    public String generateRefreshToken() {
        byte[] bytes = new byte[REFRESH_TOKEN_BYTES];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public boolean validateAccessToken(String token) {
        try {
            parseClaims(token);
            return true;
        } catch (JwtException e) {
            log.debug("Invalid JWT: {}", e.getMessage());
            return false;
        } catch (IllegalArgumentException e) {
            log.debug("Empty or malformed JWT: {}", e.getMessage());
            return false;
        }
    }

    public Long extractUserId(String token) {
        Claims claims = parseClaims(token);
        return claims.get("user_id", Long.class);
    }

    public String extractRole(String token) {
        Claims claims = parseClaims(token);
        return claims.get("role", String.class);
    }

    public OffsetDateTime extractExpiration(String token) {
        Claims claims = parseClaims(token);
        return OffsetDateTime.ofInstant(claims.getExpiration().toInstant(), ZoneOffset.UTC);
    }

    public OffsetDateTime accessTokenExpiresAt() {
        return OffsetDateTime.now(ZoneOffset.UTC).plus(properties.getAccessTokenTtl());
    }

    public OffsetDateTime refreshTokenExpiresAt() {
        return OffsetDateTime.now(ZoneOffset.UTC).plus(properties.getRefreshTokenTtl());
    }

    public int getAccessTokenTtlSeconds() {
        return (int) properties.getAccessTokenTtl().toSeconds();
    }

    public int getRefreshTokenTtlSeconds() {
        return (int) properties.getRefreshTokenTtl().toSeconds();
    }

    public UUID generateSessionId() {
        return UUID.randomUUID();
    }

    private Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(signingKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
