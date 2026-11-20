package ru.tmobile.backend.dto.familyGroup;

import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.validation.constraints.NotNull;

public record AddMemberRequest(
    @NotNull(message = "user_id is required")
    @JsonProperty("user_id")
    Long userId
) {}
