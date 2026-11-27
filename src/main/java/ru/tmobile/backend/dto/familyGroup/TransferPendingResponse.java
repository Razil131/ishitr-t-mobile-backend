package ru.tmobile.backend.dto.familyGroup;

import com.fasterxml.jackson.annotation.JsonProperty;

public record TransferPendingResponse (
    @JsonProperty("group_id")
    Long groupId,

    @JsonProperty("transfer")
    TransferPendingInfo transfer
) {}
