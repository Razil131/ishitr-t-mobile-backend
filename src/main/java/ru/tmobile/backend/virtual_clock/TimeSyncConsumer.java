package ru.tmobile.backend.virtual_clock;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;

@Component
public class TimeSyncConsumer {

    private final VirtualClockService virtualClock;

    public TimeSyncConsumer(VirtualClockService virtualClock) {
        this.virtualClock = virtualClock;
    }

    @KafkaListener(
        topics = "time-change",
        groupId = "tmobile-group"
    )
    public void consumeTimeUpdate(TimeChangeEvent event) {
        System.out.println("[TimeSync] Received TimeChangeEvent: " + event);

        long virtualStartTime = parseToEpochMillis(event.base_virtual_time());
        long realStartTime = parseToEpochMillis(event.base_real_time());

        virtualClock.syncTime(
            virtualStartTime,
            realStartTime,
            event.multiplier(),
            event.is_paused()
        );
    }

    private long parseToEpochMillis(String dateTimeStr) {
        if (dateTimeStr == null || dateTimeStr.isBlank()) {
            return System.currentTimeMillis();
        }
        try {
            return OffsetDateTime.parse(dateTimeStr).toInstant().toEpochMilli();
        } catch (Exception e) {
            System.err.println("[TimeSync] Failed to parse datetime: " + dateTimeStr);
            return System.currentTimeMillis();
        }
    }

    public record TimeChangeEvent(
        String event_id,
        String base_real_time,
        String base_virtual_time,
        int multiplier,
        boolean is_paused,
        String changed_at
    ) {}
}