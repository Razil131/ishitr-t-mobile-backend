package ru.tmobile.backend.dto.familyGroup;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record FamilyGroupCreateRequest(
    @NotNull(message = "owner_id is required")
    @JsonProperty("owner_id")
    Long ownerId,

    @NotEmpty(message = "member_ids must not be empty")
    @Size(min = 1, max = 3, message = "member_ids must contain 1 to 3 elements")
    @JsonProperty("member_ids")
    List<Long> memberIds
) {}