package ru.tmobile.backend.dto.familyGroup;

import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.validation.constraints.NotNull;

public record TransferOwnershipRequest(
    @NotNull(message = "new_owner_id is required")
    @JsonProperty("new_owner_id")
    Long newOwnerId
) {}
