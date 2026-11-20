package ru.tmobile.backend.dto.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateUserRequest(
    @NotBlank(message = "name is required")
    @Size(min = 1, max = 50, message = "name must be between 1 and 50 characters")
    String name
) {}
