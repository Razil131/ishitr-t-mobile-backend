package ru.tmobile.backend.dto.familyGroup;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record Contact (
    @JsonProperty("user_id")
    Long userId,

    @JsonProperty("phone_number")
    String phoneNumber,

    @JsonProperty("name")
    String name
) {}
