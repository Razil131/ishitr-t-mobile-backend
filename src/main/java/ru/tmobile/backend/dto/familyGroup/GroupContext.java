package ru.tmobile.backend.dto.familyGroup;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record GroupContext (
    @JsonProperty("group_id")
    Long groupId,
    
    @JsonProperty("role")
    String role,

    @JsonProperty("member_count")
    Integer memberCount
) {}
