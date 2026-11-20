package ru.tmobile.backend.dto.auth;

import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record VerifyCodeRequest(
    @NotNull(message = "session_id is required")
    UUID session_id,

    @NotBlank(message = "code is required")
    String code
) {}
