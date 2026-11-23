package ru.tmobile.backend.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import ru.tmobile.backend.config.JwtProperties;

class JwtTokenProviderTest {

	private static final String SECRET = "0123456789012345678901234567890123456789";

	private JwtProperties properties;
	private JwtTokenProvider provider;

	@BeforeEach
	void setUp() {
		properties = new JwtProperties();
		properties.setSecret(SECRET);
		properties.setAccessTokenTtl(Duration.ofMinutes(15));
		properties.setRefreshTokenTtl(Duration.ofDays(30));
		provider = new JwtTokenProvider(properties);
	}

	@Test
	void generateAccessTokenCreatesThreePartJwtWithRequiredClaims() {
		String token = provider.generateAccessToken(42L, "USER");

		assertEquals(3, token.split("\\.").length);
		Claims claims = Jwts.parser()
				.verifyWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8)))
				.build()
				.parseSignedClaims(token)
				.getPayload();
		assertEquals("42", claims.getSubject());
		assertEquals(42L, claims.get("user_id", Long.class));
		assertEquals("USER", claims.get("role", String.class));
		assertNotNull(claims.getIssuedAt());
		assertNotNull(claims.getExpiration());
	}

	@Test
	void validateAccessTokenAcceptsFreshToken() {
		assertTrue(provider.validateAccessToken(provider.generateAccessToken(1L, "MEMBER")));
	}

	@Test
	void validateAccessTokenRejectsTokenSignedWithAnotherKey() {
		JwtProperties otherProperties = new JwtProperties();
		otherProperties.setSecret("99999999999999999999999999999999");
		otherProperties.setAccessTokenTtl(Duration.ofMinutes(15));
		JwtTokenProvider otherProvider = new JwtTokenProvider(otherProperties);

		String token = provider.generateAccessToken(1L, "USER");

		assertFalse(otherProvider.validateAccessToken(token));
	}

	@Test
	void validateAccessTokenRejectsTamperedToken() {
		String token = provider.generateAccessToken(1L, "USER");
		int lastDot = token.lastIndexOf('.');
		String tampered = token.substring(0, lastDot) + "." + token.substring(lastDot + 1).replaceFirst(".", "A");

		assertFalse(provider.validateAccessToken(tampered));
	}

	@Test
	void validateAccessTokenRejectsNullEmptyAndMalformedTokens() {
		assertFalse(provider.validateAccessToken(null));
		assertFalse(provider.validateAccessToken(""));
		assertFalse(provider.validateAccessToken("not-a-jwt"));
	}

	@Test
	void validateAccessTokenRejectsExpiredTokenWithoutWallClockDependency() {
		properties.setAccessTokenTtl(Duration.ofSeconds(-10));
		JwtTokenProvider expiredProvider = new JwtTokenProvider(properties);
		String token = expiredProvider.generateAccessToken(1L, "USER");

		assertFalse(expiredProvider.validateAccessToken(token));
	}

	@Test
	void extractUserIdReadsClaimFromToken() {
		String token = provider.generateAccessToken(777L, "USER");
		assertEquals(777L, provider.extractUserId(token));
	}

	@Test
	void extractExpirationReturnsUtcTimeInFutureForPositiveTtl() {
		String token = provider.generateAccessToken(1L, "USER");
		OffsetDateTime expiration = provider.extractExpiration(token);

		assertEquals(ZoneOffset.UTC, expiration.getOffset());
		assertTrue(expiration.isAfter(OffsetDateTime.now(ZoneOffset.UTC)));
	}

	@Test
	void accessTokenExpiresAtUsesRealTimeAndConfiguredTtl() {
		OffsetDateTime before = OffsetDateTime.now(ZoneOffset.UTC);
		OffsetDateTime actual = provider.accessTokenExpiresAt();
		OffsetDateTime after = OffsetDateTime.now(ZoneOffset.UTC);

		assertFalse(actual.isBefore(before.plusMinutes(15).minusSeconds(1)));
		assertFalse(actual.isAfter(after.plusMinutes(15).plusSeconds(1)));
	}

	@Test
	void refreshTokenExpiresAtUsesRealTimeAndConfiguredTtl() {
		OffsetDateTime before = OffsetDateTime.now(ZoneOffset.UTC);
		OffsetDateTime actual = provider.refreshTokenExpiresAt();
		OffsetDateTime after = OffsetDateTime.now(ZoneOffset.UTC);

		assertFalse(actual.isBefore(before.plusDays(30).minusSeconds(1)));
		assertFalse(actual.isAfter(after.plusDays(30).plusSeconds(1)));
	}

	@Test
	void ttlGettersReturnWholeSeconds() {
		properties.setAccessTokenTtl(Duration.ofSeconds(900));
		properties.setRefreshTokenTtl(Duration.ofSeconds(2_592_000));

		assertEquals(900, provider.getAccessTokenTtlSeconds());
		assertEquals(2_592_000, provider.getRefreshTokenTtlSeconds());
	}

	@Test
	void generateRefreshTokenReturnsUnpaddedBase64UrlWith32RandomBytes() {
		String token = provider.generateRefreshToken();

		assertTrue(token.matches("[A-Za-z0-9_-]+"));
		assertEquals(32, Base64.getUrlDecoder().decode(token).length);
	}

	@Test
	void generateRefreshTokenProducesUniqueValues() {
		Set<String> tokens = new HashSet<>();
		for (int i = 0; i < 200; i++) {
			tokens.add(provider.generateRefreshToken());
		}
		assertEquals(200, tokens.size());
	}

	@Test
	void generateSessionIdProducesUniqueUuids() {
		Set<Object> ids = new HashSet<>();
		for (int i = 0; i < 200; i++) {
			ids.add(provider.generateSessionId());
		}
		assertEquals(200, ids.size());
	}
}
