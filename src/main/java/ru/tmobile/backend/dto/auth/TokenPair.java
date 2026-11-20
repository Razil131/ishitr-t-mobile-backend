package ru.tmobile.backend.dto.auth;

import java.time.OffsetDateTime;

public record TokenPair(
    String access_token,
    String refresh_token,
    Integer access_token_ttl_seconds,
    Integer refresh_token_ttl_seconds,
    OffsetDateTime access_token_expires_at,
    OffsetDateTime refresh_token_expires_at
) {}
