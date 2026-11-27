package ru.tmobile.backend.dto.familyGroup;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

public record UserSearchResponse (
    @JsonProperty("results")
    List<Contact> results
) {}
