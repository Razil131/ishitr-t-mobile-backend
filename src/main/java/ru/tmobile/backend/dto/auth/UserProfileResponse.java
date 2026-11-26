package ru.tmobile.backend.dto.auth;

import com.fasterxml.jackson.annotation.JsonInclude;

import ru.tmobile.backend.dto.familyGroup.GroupContext;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record UserProfileResponse (
    UserProfile user,
    GroupContext group
) {}
