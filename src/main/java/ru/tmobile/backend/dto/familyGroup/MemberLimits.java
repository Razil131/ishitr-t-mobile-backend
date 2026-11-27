package ru.tmobile.backend.dto.familyGroup;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

public record MemberLimits (
    @JsonProperty("user_id")
    Long userId,

    @JsonProperty("phone_number")
    String phoneNumber,

    @JsonProperty("name")
    String name,

    @JsonProperty("role")
    String role,

    @JsonProperty("limits")
    List<UserLimitDetail> limits
) {}
