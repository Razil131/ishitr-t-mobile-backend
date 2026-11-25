package ru.tmobile.backend.dto.familyGroup;

import java.time.OffsetDateTime;

import com.fasterxml.jackson.annotation.JsonProperty;

import ru.tmobile.backend.entity.FamilyGroupEntity.GroupStatus;

public record FamilyGroupResponse(
    @JsonProperty("group_id")
    Long groupId,

    @JsonProperty("owner_id") 
    Long ownerId,

    @JsonProperty("status") 
    GroupStatus status,

    @JsonProperty("created_at") 
    OffsetDateTime createdAt,

    @JsonProperty("members_count") 
    int membersCount
) {}
