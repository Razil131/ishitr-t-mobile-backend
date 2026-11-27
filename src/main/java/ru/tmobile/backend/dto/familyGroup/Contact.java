package ru.tmobile.backend.dto.familyGroup;

import com.fasterxml.jackson.annotation.JsonProperty;

public record Contact (
    @JsonProperty("user_id")
    Long userId,

    @JsonProperty("phone_number")
    String phoneNumber,

    @JsonProperty("name")
    String name
) {}
