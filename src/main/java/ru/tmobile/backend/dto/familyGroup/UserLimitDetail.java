package ru.tmobile.backend.dto.familyGroup;

import com.fasterxml.jackson.annotation.JsonProperty;

public record UserLimitDetail (
    @JsonProperty("resource_type")
    String resourceType,

    @JsonProperty("limit_value")
    Double limitValue,

    @JsonProperty("used_value")
    Double usedValue,

    @JsonProperty("status")
    String status
) {}
