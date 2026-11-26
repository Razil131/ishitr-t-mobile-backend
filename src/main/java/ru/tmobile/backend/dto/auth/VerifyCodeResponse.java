package ru.tmobile.backend.dto.auth;

import ru.tmobile.backend.dto.familyGroup.GroupContext;

public record VerifyCodeResponse(
    TokenPair tokens,
    UserProfile user,
    Boolean is_new_user,
    GroupContext group
) {}
