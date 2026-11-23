package ru.tmobile.backend;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.time.OffsetDateTime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import ru.tmobile.backend.dto.ConsumptionEventDto;
import ru.tmobile.backend.service.ConsumptionEventService;

@ExtendWith(MockitoExtension.class)
class ConsumptionEventListenerTest {
    @Mock ConsumptionEventService service;
    ConsumptionEventListener listener;

    @BeforeEach void up() { listener = new ConsumptionEventListener(service); }

    @Test void skipsNullEvent() {
        assertDoesNotThrow(() -> listener.listenConsumptionEvents(null));
        verify(service, never()).saveConsumptionEvent(any(), any(), any(), any(), any(), any());
    }

    @Test void skipsEventWithMissingRequiredFields() {
        ConsumptionEventDto dto = new ConsumptionEventDto(null, 1L, "TRAFFIC", 10.0, "2026-01-01T00:00:00Z", null);
        listener.listenConsumptionEvents(dto);
        verify(service, never()).saveConsumptionEvent(any(), any(), any(), any(), any(), any());
    }

    @Test void forwardsValidEventToService() {
        String ts = "2026-01-15T12:00:00Z";
        ConsumptionEventDto dto = new ConsumptionEventDto("e1", 1L, "TRAFFIC", 25.5, ts, 7L);
        listener.listenConsumptionEvents(dto);
        verify(service).saveConsumptionEvent(eq("e1"), eq(1L), eq("TRAFFIC"), eq(25.5),
            eq(OffsetDateTime.parse(ts)), eq(7L));
    }

    @Test void catchesExceptionFromServiceWithoutPropagating() {
        ConsumptionEventDto dto = new ConsumptionEventDto("e2", 2L, "MINUTES", 3.0, "2026-01-15T12:00:00Z", 8L);
        doThrow(new RuntimeException("db down")).when(service)
            .saveConsumptionEvent(any(), any(), any(), any(), any(), any());
        assertDoesNotThrow(() -> listener.listenConsumptionEvents(dto));
    }

    @Test void failsGracefullyOnMalformedTimestamp() {
        ConsumptionEventDto dto = new ConsumptionEventDto("e3", 1L, "TRAFFIC", 1.0, "not-a-date", null);
        assertDoesNotThrow(() -> listener.listenConsumptionEvents(dto));
        verify(service, never()).saveConsumptionEvent(any(), any(), any(), any(), any(), any());
    }
}
