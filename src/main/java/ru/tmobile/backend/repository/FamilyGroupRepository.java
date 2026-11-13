package ru.tmobile.backend.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.tmobile.backend.entity.FamilyGroupEntity;
import ru.tmobile.backend.entity.FamilyGroupEntity.GroupStatus;

public interface FamilyGroupRepository extends JpaRepository<FamilyGroupEntity, Long> {
    Optional<FamilyGroupEntity> findByIdAndStatus(Long id, GroupStatus status);
}
