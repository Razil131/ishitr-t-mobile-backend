package ru.tmobile.backend.dto.auth;

public record RefreshResponse(
    TokenPair tokens
){}
