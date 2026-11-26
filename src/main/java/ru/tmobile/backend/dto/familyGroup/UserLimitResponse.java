package ru.tmobile.backend.dto.familyGroup;

import com.fasterxml.jackson.annotation.JsonProperty;

public record UserLimitResponse(
    @JsonProperty("user_id")
    Long userId,

    @JsonProperty("group_id")
    Long groupId,
    
    @JsonProperty("resource_type")
    String resourceType,
    
    @JsonProperty("limit_value")
    Double limitValue,
    
    @JsonProperty("used_value")
    Double usedValue,
    
    @JsonProperty("status")
    String status,

    @JsonProperty("min_allowed")
    Double minAllowed,
    
    @JsonProperty("max_allowed")
    Double maxAllowed
) {}
