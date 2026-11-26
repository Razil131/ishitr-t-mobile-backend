package ru.tmobile.backend.dto.familyGroup;

import java.time.OffsetDateTime;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record GroupMemberDetail(
    @JsonProperty("user_id")
    Long userId,

    @JsonProperty("phone_number")
    String phoneNumber,

    @JsonProperty("name")
    String name,

    @JsonProperty("role")
    String role,

    @JsonProperty("status")
    String status,

    @JsonProperty("joined_at")
    OffsetDateTime joinedAt,

    @JsonProperty("left_at")
    OffsetDateTime leftAt,

    @JsonProperty("limits")
    List<UserLimitDetail> limits
){}
