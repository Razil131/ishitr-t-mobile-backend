package ru.tmobile.backend.virtual_clock;

import static org.mockito.Mockito.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TimeSyncConsumerTest {
    @Mock VirtualClockService clock;
    TimeSyncConsumer consumer;
    // 2026-01-01T00:00:00Z in epoch millis
    static final long EPOCH_2026_01_01 = 1767225600000L;

    @BeforeEach void up() { consumer = new TimeSyncConsumer(clock); }

    @Test void forwardsValidEventToClock() {
        TimeSyncConsumer.TimeChangeEvent e = new TimeSyncConsumer.TimeChangeEvent(
            "id-1", "2026-01-01T00:00:00Z", "2026-01-01T00:00:00Z", 3, false, "2026-01-01T00:00:00Z");
        consumer.consumeTimeUpdate(e);
        verify(clock).syncTime(eq(EPOCH_2026_01_01), eq(EPOCH_2026_01_01), eq(3), eq(false));
    }

    @Test void usesCurrentTimeWhenRealTimeFieldIsBlank() {
        long before = System.currentTimeMillis();
        TimeSyncConsumer.TimeChangeEvent e = new TimeSyncConsumer.TimeChangeEvent(
            "id-2", "", "2026-01-01T00:00:00Z", 1, true, "2026-01-01T00:00:00Z");
        consumer.consumeTimeUpdate(e);
        verify(clock).syncTime(eq(EPOCH_2026_01_01), longThat(v -> v >= before && v <= System.currentTimeMillis() + 100), eq(1), eq(true));
    }

    @Test void usesCurrentTimeWhenVirtualTimeFieldIsNull() {
        long before = System.currentTimeMillis();
        TimeSyncConsumer.TimeChangeEvent e = new TimeSyncConsumer.TimeChangeEvent(
            "id-3", "2026-01-01T00:00:00Z", null, 2, false, "2026-01-01T00:00:00Z");
        consumer.consumeTimeUpdate(e);
        verify(clock).syncTime(longThat(v -> v >= before && v <= System.currentTimeMillis() + 100), eq(EPOCH_2026_01_01), eq(2), eq(false));
    }

    @Test void usesCurrentTimeWhenDateFormatIsInvalid() {
        long before = System.currentTimeMillis();
        TimeSyncConsumer.TimeChangeEvent e = new TimeSyncConsumer.TimeChangeEvent(
            "id-4", "garbage", "also-garbage", 5, true, "2026-01-01T00:00:00Z");
        consumer.consumeTimeUpdate(e);
        verify(clock).syncTime(longThat(v -> v >= before && v <= System.currentTimeMillis() + 100),
            longThat(v -> v >= before && v <= System.currentTimeMillis() + 100), eq(5), eq(true));
    }
}