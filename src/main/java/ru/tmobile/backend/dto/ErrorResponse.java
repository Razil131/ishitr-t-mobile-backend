package ru.tmobile.backend.dto;

import java.util.Map;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ErrorResponse(
    ErrorDetail error
) {
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ErrorDetail(
        String code,
        String message,
        Map<String, Object> details,
        String trace_id
    ) {}

    public static ErrorResponse of(String code, String message) {
        return new ErrorResponse(new ErrorDetail(code, message, null, null));
    }

    public static ErrorResponse of(String code, String message, Map<String, Object> details, String traceId) {
        return new ErrorResponse(new ErrorDetail(code, message, details, traceId));
    }
}
