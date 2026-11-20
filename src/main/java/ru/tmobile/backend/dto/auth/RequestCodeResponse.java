package ru.tmobile.backend.dto.auth;

import java.time.OffsetDateTime;
import java.util.UUID;

public record RequestCodeResponse(
    UUID session_id,
    String code,
    Integer code_ttl_seconds,
    Integer cooldown_seconds,
    OffsetDateTime expires_at
) {}
