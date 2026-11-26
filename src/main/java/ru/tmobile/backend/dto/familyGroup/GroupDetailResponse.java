package ru.tmobile.backend.dto.familyGroup;

import java.time.OffsetDateTime;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import ru.tmobile.backend.entity.FamilyGroupEntity.GroupStatus;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record GroupDetailResponse (
    @JsonProperty("group_id")
    Long groupId,

    @JsonProperty("name")
    String name,
    
    @JsonProperty("owner_id")
    Long OwnerId,

    @JsonProperty("status")
    GroupStatus status,
    
    @JsonProperty("created_at")
    OffsetDateTime createdAt,
    
    @JsonProperty("member_count")
    Integer memberCount,
    
    @JsonProperty("transfer_pending")
    TransferPendingInfo transferPending,
    
    @JsonProperty("members")
    List<GroupMemberDetail> members,
    
    @JsonProperty("package")
    PackageInfo packageInfo
) {}
