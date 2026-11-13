package ru.tmobile.backend.dto;

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
    private String eventId;

    @NotNull(message = "user_id is required")
    @JsonProperty("user_id")
    private Long userId;

    @NotBlank(message = "resource_type is required")
    @JsonProperty("resource_type")
    private String resourceType;

    @NotNull(message = "volume is required")
    @JsonProperty("volume")
    private java.math.BigDecimal volume;

    @NotBlank(message = "event_timestamp is required")
    @JsonProperty("event_timestamp")
    private String eventTimestamp;

    @JsonProperty("is_anomaly")
    private Boolean isAnomaly = false;

    @JsonProperty("group_id")
    private Long groupId;
}
