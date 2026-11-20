package ru.tmobile.backend.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ConsumptionEventDto(
    @NotBlank(message = "event_id is required")
    @JsonProperty("event_id")
    @JsonAlias("eventId")
    String eventId,

    @NotNull(message = "user_id is required")
    @JsonProperty("user_id")
    @JsonAlias("userId")
    Long userId,

    @NotBlank(message = "resource_type is required")
    @JsonProperty("resource_type")
    @JsonAlias("resourceType")
    String resourceType,

    @NotNull(message = "amount is required")
    @JsonProperty("amount")
    Double amount,

    @NotBlank(message = "event_timestamp is required")
    @JsonProperty("event_timestamp")
    @JsonAlias("eventTimestamp")
    String eventTimestamp,

    @JsonProperty("group_id")
    @JsonAlias("groupId")
    Long groupId
) {}
