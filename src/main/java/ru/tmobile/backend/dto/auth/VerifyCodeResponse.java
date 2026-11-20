package ru.tmobile.backend.dto.auth;

public record VerifyCodeResponse(
    TokenPair tokens,
    UserProfile user,
    Boolean is_new_user,
    Object group // TODO должно быть GroupContext
) {}
