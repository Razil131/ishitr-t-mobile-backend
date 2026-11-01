package ru.tmobile.backend.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.tmobile.backend.entity.ConsumptionEventEntity;

import java.util.List;

public interface ConsumptionEventRepository extends JpaRepository<ConsumptionEventEntity, String> {
    List<ConsumptionEventEntity> findByUserId(Long userId);
    boolean existsByEventId(String eventId);
}