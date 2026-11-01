package ru.tmobile.backend;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;
import ru.tmobile.backend.service.ConsumptionEventService;

import java.time.OffsetDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class ConsumptionEventListener {

    private final ConsumptionEventService consumptionEventService;

    @KafkaListener(topics = "consumption-events", groupId = "tmobile-group", containerFactory = "stringKafkaListenerContainerFactory")
    public void listenConsumptionEvents(String message) {
        try {
            String eventId = extractValue(message, "event_id");
            Long userId = Long.parseLong(extractValue(message, "user_id"));
            String resourceType = extractValue(message, "resource_type");
            Double amount = Double.parseDouble(extractValue(message, "amount"));
            OffsetDateTime eventTimestamp = OffsetDateTime.parse(extractValue(message, "event_timestamp"));
            Boolean isAnomaly = Boolean.parseBoolean(extractValue(message, "is_anomaly"));
            String groupIdStr = extractValue(message, "group_id");
            Long groupId = (groupIdStr != null && !groupIdStr.isEmpty()) ? Long.parseLong(groupIdStr) : null;

            consumptionEventService.saveConsumptionEvent(eventId, userId, resourceType, amount, eventTimestamp, isAnomaly, groupId);

        } catch (Exception e) {
            log.error("Failed to process consumption event: {}", message, e);
        }
    }

    private String extractValue(String message, String key) {
        String[] patterns = {key + ":", "\"" + key + "\":", "\"" + key + "\" :"};
        for (String pattern : patterns) {
            int idx = message.indexOf(pattern);
            if (idx >= 0) {
                int start = idx + pattern.length();
                while (start < message.length() && message.charAt(start) == ' ') start++;
                if (start < message.length() && message.charAt(start) == '"') {
                    int end = message.indexOf('"', start + 1);
                    return message.substring(start + 1, end);
                } else {
                    int end = start;
                    while (end < message.length() && message.charAt(end) != ',' && message.charAt(end) != '}') end++;
                    return message.substring(start, end).trim();
                }
            }
        }
        return null;
    }
}