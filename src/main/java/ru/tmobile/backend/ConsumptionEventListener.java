package ru.tmobile.backend;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;
import ru.tmobile.backend.dto.ConsumptionEventDto;
import ru.tmobile.backend.service.ConsumptionEventService;

import java.time.OffsetDateTime;

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

        if (event.getEventTimestamp() == null || event.getEventId() == null || event.getUserId() == null || event.getResourceType() == null) {
            log.warn("Received consumption event with missing required fields, skipping: {}", event);
            return;
        }

        try {
            OffsetDateTime eventTimestamp = OffsetDateTime.parse(event.getEventTimestamp());

            consumptionEventService.saveConsumptionEvent(
                    event.getEventId(),
                    event.getUserId(),
                    event.getResourceType(),
                    event.getAmount(),
                    eventTimestamp,
                    event.getGroupId()
            );
        } catch (Exception e) {
            log.error("Failed to process consumption event: {}", event, e);
        }
    }
}