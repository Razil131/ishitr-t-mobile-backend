package ru.tmobile.backend.dto.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record RequestCodeRequest(
    @NotBlank(message = "phone_number is required")
    @Pattern(regexp = "^\\+7\\d{10}$", message = "phone_number must be in format +7XXXXXXXXXX")
    String phone_number
) {}
