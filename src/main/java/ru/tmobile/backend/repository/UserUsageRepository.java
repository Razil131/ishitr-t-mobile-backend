package ru.tmobile.backend.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import ru.tmobile.backend.entity.UserUsageEntity;
import ru.tmobile.backend.entity.enums.ResourceType;

import java.util.List;
import java.util.Optional;

public interface UserUsageRepository extends JpaRepository<UserUsageEntity, UserUsageEntity.UserUsageId> {
    Optional<UserUsageEntity> findByUserIdAndGroupIdAndPeriodIdAndResourceType(Long userId, Long groupId, Long periodId, ResourceType resourceType);
    List<UserUsageEntity> findAllByGroupIdAndPeriodId(Long groupId, Long periodId);
}