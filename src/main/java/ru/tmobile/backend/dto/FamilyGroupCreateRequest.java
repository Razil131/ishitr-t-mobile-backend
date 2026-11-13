package ru.tmobile.backend.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class FamilyGroupCreateRequest {

    @NotNull(message = "owner_id is required")
    @JsonProperty("owner_id")
    private Long ownerId;

    @NotEmpty(message = "member_ids must not be empty")
    @Size(min = 1, max = 3, message = "member_ids must contain 1 to 3 elements")
    @JsonProperty("member_ids")
    private List<Long> memberIds;
}