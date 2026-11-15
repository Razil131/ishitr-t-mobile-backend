package ru.tmobile.backend.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class ConsumptionEventDto {

    @NotBlank(message = "event_id is required")
    @JsonProperty("event_id")
    @JsonAlias("eventId")
    private String eventId;

    @NotNull(message = "user_id is required")
    @JsonProperty("user_id")
    @JsonAlias("userId")
    private Long userId;

    @NotBlank(message = "resource_type is required")
    @JsonProperty("resource_type")
    @JsonAlias("resourceType")
    private String resourceType;

    @NotNull(message = "amount is required")
    @JsonProperty("amount")
    private Double amount;

    @NotBlank(message = "event_timestamp is required")
    @JsonProperty("event_timestamp")
    @JsonAlias("eventTimestamp")
    private String eventTimestamp;

    @JsonProperty("group_id")
    @JsonAlias("groupId")
    private Long groupId;
}
