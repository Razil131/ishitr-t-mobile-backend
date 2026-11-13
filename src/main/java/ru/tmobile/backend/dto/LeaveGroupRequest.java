package ru.tmobile.backend.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class LeaveGroupRequest {

    @NotNull(message = "user_id is required")
    @JsonProperty("user_id")
    private Long userId;
}