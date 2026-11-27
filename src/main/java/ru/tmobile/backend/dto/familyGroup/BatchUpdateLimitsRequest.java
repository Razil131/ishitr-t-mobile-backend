package ru.tmobile.backend.dto.familyGroup;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

public record BatchUpdateLimitsRequest (
    @Valid
    @NotEmpty 
    @JsonProperty("updates")
    List<LimitUpdateItem> updates
) {}
