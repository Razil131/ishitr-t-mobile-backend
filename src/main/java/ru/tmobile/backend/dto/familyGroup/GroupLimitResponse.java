package ru.tmobile.backend.dto.familyGroup;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

public record GroupLimitResponse (
    @JsonProperty("group_id")
    Long groupId,

    @JsonProperty("members")
    List<MemberLimits> members
) {}
