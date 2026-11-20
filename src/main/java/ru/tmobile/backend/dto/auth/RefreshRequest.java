package ru.tmobile.backend.dto.auth;

import jakarta.validation.constraints.NotBlank;

public record RefreshRequest(
    @NotBlank(message = "refresh_token is required")
    String refresh_token
) {}
