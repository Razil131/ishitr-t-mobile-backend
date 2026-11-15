package ru.tmobile.backend.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.tmobile.backend.entity.UserLimitEntity;
import ru.tmobile.backend.entity.enums.ResourceType;

import java.util.List;
import java.util.Optional;

public interface UserLimitRepository extends JpaRepository<UserLimitEntity, UserLimitEntity.UserLimitId> {
    Optional<UserLimitEntity> findByUserIdAndGroupIdAndResourceType(Long userId, Long groupId, ResourceType resourceType);
    List<UserLimitEntity> findAllByGroupIdAndResourceType(Long groupId, ResourceType resourceType);
}