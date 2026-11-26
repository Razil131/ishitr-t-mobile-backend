package ru.tmobile.backend.dto.familyGroup;

import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.validation.constraints.NotNull;

public record SetLimitRequest (
    @NotNull @JsonProperty("resource_type")
    String resourceType,

    @NotNull @JsonProperty("limit_value")
    Double limitValue
) {}
