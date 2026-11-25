package ru.tmobile.backend;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

import ru.tmobile.backend.dto.ConsumptionEventDto;
import ru.tmobile.backend.entity.enums.ResourceType;
import ru.tmobile.backend.service.ConsumptionEventService;

import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.Arrays;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ConsumptionEventListener {

    private final ConsumptionEventService consumptionEventService;

    @KafkaListener(topics = "consumption-events", groupId = "tmobile-group", containerFactory = "consumptionEventKafkaListenerContainerFactory")
    public void listenConsumptionEvents(ConsumptionEventDto event) {
        if (event == null) {
            log.warn("Received null consumption event, skipping");
            return;
        }

        if (event.eventTimestamp() == null || event.eventId() == null || event.userId() == null || event.resourceType() == null || event.amount() == null) {
            log.warn("Received consumption event with missing required fields, skipping: {}", event);
            return;
        }

        if (event.amount() <= 0){
            log.warn("Invalid amount={} for eventId={}, skipping", event.amount(), event.eventId());
            return;
        }

        List<String> validResourceType = Arrays.stream(ResourceType.values()).map(ResourceType::name).toList();
        if (!validResourceType.contains(event.resourceType())) {
            log.warn("Invalid resource_type={} for eventId={}, skipping", event.resourceType(), event.eventId());
            return;
        }

        OffsetDateTime eventTimestamp;
        try {
            eventTimestamp = OffsetDateTime.parse(event.eventTimestamp());
        } catch (DateTimeParseException e) {
            log.warn("Malformed event_timestamp={} for eventId={}, skipping", event.eventTimestamp(), event.eventId());
            return;
        }
        
        try {
            consumptionEventService.saveConsumptionEvent(
            event.eventId(),
            event.userId(),
            event.resourceType(),
            event.amount(),
            eventTimestamp,
            event.groupId()
            );
        } catch (RuntimeException e) {
            log.error("Failed to process consumption event: eventId={}, userId={}, will be retried by broker",
                event.eventId(), event.userId(), e);
            throw e;
        }

    }
}