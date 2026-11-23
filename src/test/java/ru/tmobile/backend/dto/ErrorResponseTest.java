package ru.tmobile.backend.dto;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.Map;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

class ErrorResponseTest {

    @Test void simpleFactorySetsCodeAndMessageOnly() {
        ErrorResponse r = ErrorResponse.of("CODE", "message");
        assertEquals("CODE", r.error().code());
        assertEquals("message", r.error().message());
        assertNull(r.error().details());
        assertNull(r.error().trace_id());
    }

    @Test void fullFactoryPreservesDetailsAndTraceId() {
        Map<String, Object> details = Map.of("field", "value");
        ErrorResponse r = ErrorResponse.of("CODE", "msg", details, "trace-1");
        assertEquals(details, r.error().details());
        assertEquals("trace-1", r.error().trace_id());
    }

    @Test void jsonOmitsNullFields() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        String json = mapper.writeValueAsString(ErrorResponse.of("CODE", "msg"));
        assertEquals("{\"error\":{\"code\":\"CODE\",\"message\":\"msg\"}}", json);
    }

    @Test void jsonIncludesDetailsWhenPresent() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        String json = mapper.writeValueAsString(
            ErrorResponse.of("CODE", "msg", Map.of("phone", "invalid"), null));
        if (!json.contains("\"details\"")) throw new AssertionError("details missing: " + json);
        if (!json.contains("\"phone\":\"invalid\"")) throw new AssertionError("phone missing: " + json);
    }
}
