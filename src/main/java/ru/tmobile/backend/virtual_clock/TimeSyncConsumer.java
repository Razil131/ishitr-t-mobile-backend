package ru.tmobile.backend.virtual_clock;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class TimeSyncConsumer {

    private final VirtualClockService virtualClock;

    public TimeSyncConsumer(VirtualClockService virtualClock) {
        this.virtualClock = virtualClock;
    }

    @KafkaListener(
        topics = "time-sync",
        groupId = "tmobile-group",
        containerFactory = "kafkaListenerContainerFactory"
    )
    public void consumeTimeUpdate(TimeUpdateEvent event) {
        System.out.println("RECEIVED EVENT: " + event);

        virtualClock.syncTime(
            event.virtualStartTime(),
            event.realStartTime(),
            event.multiplier(),
            event.isPaused()
        );
    }

    public record TimeUpdateEvent(
        long virtualStartTime,
        long realStartTime,
        double multiplier,
        boolean isPaused
    ) {}
}