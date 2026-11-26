package ru.tmobile.backend.dto.familyGroup;

import java.time.OffsetDateTime;

import com.fasterxml.jackson.annotation.JsonProperty;

public record TransferPendingInfo (
    @JsonProperty("from_user_id")
    Long fromUserId,

    @JsonProperty("to_user_id")
    Long toUserId,

    @JsonProperty("initiated_at")
    OffsetDateTime initiatedAt
) {}
