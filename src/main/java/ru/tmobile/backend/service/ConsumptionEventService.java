package ru.tmobile.backend.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.tmobile.backend.entity.ConsumptionEventEntity;
import ru.tmobile.backend.repository.ConsumptionEventRepository;

import java.time.OffsetDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class ConsumptionEventService {

    private final ConsumptionEventRepository repository;

    @Transactional
    public void saveConsumptionEvent(
            String eventId,
            Long userId,
            String resourceType,
            Double amount,
            OffsetDateTime eventTimestamp,
            Boolean isAnomaly,
            Long groupId
    ) {
        ConsumptionEventEntity entity = new ConsumptionEventEntity();
        entity.setEventId(eventId);
        entity.setUserId(userId);
        entity.setResourceType(ConsumptionEventEntity.ResourceType.valueOf(resourceType));
        entity.setAmount(amount);
        entity.setEventTimestamp(eventTimestamp);
        entity.setIsAnomaly(isAnomaly);
        entity.setProcessedAt(OffsetDateTime.now());
        entity.setGroupId(groupId);

        repository.save(entity);
        log.info("Saved consumption event: id={}, user={}, type={}", eventId, userId, resourceType);
    }
}