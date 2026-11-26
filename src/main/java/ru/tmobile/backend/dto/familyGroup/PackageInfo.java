package ru.tmobile.backend.dto.familyGroup;

import java.time.OffsetDateTime;

import com.fasterxml.jackson.annotation.JsonProperty;

public record PackageInfo (
    @JsonProperty("package_id")
    Long packageId,

    @JsonProperty("name")
    String name,

    @JsonProperty("total_traffic_gb")
    Double totalTrafficGb,

    @JsonProperty("total_minutes")
    Long totalMinutes,

    @JsonProperty("period_start")
    OffsetDateTime periodStart,

    @JsonProperty("period_end")
    OffsetDateTime periodEnd
) {}
