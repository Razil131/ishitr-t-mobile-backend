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

    @Test void skipsEventWithNullAmount() {
        ConsumptionEventDto dto = new ConsumptionEventDto("e", 1L, "TRAFFIC", null, "2026-01-01T00:00:00Z", 5L);
        listener.listenConsumptionEvents(dto);
        verify(service, never()).saveConsumptionEvent(any(), any(), any(), any(), any(), any());
    }

    @Test void skipsEventWithZeroAmount() {
        ConsumptionEventDto dto = new ConsumptionEventDto("e", 1L, "TRAFFIC", 0.0, "2026-01-01T00:00:00Z", 5L);
        listener.listenConsumptionEvents(dto);
        verify(service, never()).saveConsumptionEvent(any(), any(), any(), any(), any(), any());
    }

    @Test void skipsEventWithNegativeAmount() {
        ConsumptionEventDto dto = new ConsumptionEventDto("e", 1L, "TRAFFIC", -5.0, "2026-01-01T00:00:00Z", 5L);
        listener.listenConsumptionEvents(dto);
        verify(service, never()).saveConsumptionEvent(any(), any(), any(), any(), any(), any());
    }

    @Test void skipsEventWithInvalidResourceType() {
        ConsumptionEventDto dto = new ConsumptionEventDto("e", 1L, "SMS", 10.0, "2026-01-01T00:00:00Z", 5L);
        listener.listenConsumptionEvents(dto);
        verify(service, never()).saveConsumptionEvent(any(), any(), any(), any(), any(), any());
    }

    @Test void forwardsValidTrafficEventToService() {
        String ts = "2026-01-15T12:00:00Z";
        ConsumptionEventDto dto = new ConsumptionEventDto("e1", 1L, "TRAFFIC", 25.5, ts, 7L);
        listener.listenConsumptionEvents(dto);
        verify(service).saveConsumptionEvent(eq("e1"), eq(1L), eq("TRAFFIC"), eq(25.5),
            eq(OffsetDateTime.parse(ts)), eq(7L));
    }

    @Test void forwardsValidMinutesEventToService() {
        String ts = "2026-01-15T12:00:00Z";
        ConsumptionEventDto dto = new ConsumptionEventDto("e1", 1L, "MINUTES", 3.0, ts, null);
        listener.listenConsumptionEvents(dto);
        verify(service).saveConsumptionEvent(eq("e1"), eq(1L), eq("MINUTES"), eq(3.0),
            eq(OffsetDateTime.parse(ts)), isNull());
    }

    @Test void skipsEventWithMalformedTimestamp() {
        ConsumptionEventDto dto = new ConsumptionEventDto("e3", 1L, "TRAFFIC", 1.0, "not-a-date", 5L);
        assertDoesNotThrow(() -> listener.listenConsumptionEvents(dto));
        verify(service, never()).saveConsumptionEvent(any(), any(), any(), any(), any(), any());
    }

    @Test void propagatesUnexpectedExceptionFromService() {
        ConsumptionEventDto dto = new ConsumptionEventDto("e2", 2L, "MINUTES", 3.0, "2026-01-15T12:00:00Z", 8L);
        RuntimeException ex = new RuntimeException("db down");
        doThrow(ex).when(service).saveConsumptionEvent(any(), any(), any(), any(), any(), any());
        RuntimeException thrown = assertThrows(RuntimeException.class, () -> listener.listenConsumptionEvents(dto));
        assertSame(ex, thrown);
    }
}