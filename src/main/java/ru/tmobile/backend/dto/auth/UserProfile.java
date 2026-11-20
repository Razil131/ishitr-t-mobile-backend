package ru.tmobile.backend.dto.auth;

import java.time.OffsetDateTime;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record UserProfile(
    Long user_id,
    String phone_number,
    String name,
    OffsetDateTime created_at
){}
