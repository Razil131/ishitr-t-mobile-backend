package ru.tmobile.backend.dto.familyGroup;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

public record LimitUpdateItem (
    @NotNull 
    @JsonProperty("user_id")
    Long userId,

    @Valid 
    @NotEmpty 
    @JsonProperty("limits")
    List<SetLimitRequest> limits
) {}
