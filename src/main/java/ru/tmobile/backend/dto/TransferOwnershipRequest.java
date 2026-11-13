package ru.tmobile.backend.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class TransferOwnershipRequest {
    @NotNull(message = "new_owner_id is required")
    @JsonProperty("new_owner_id")
    private Long newOwnerId;
}
