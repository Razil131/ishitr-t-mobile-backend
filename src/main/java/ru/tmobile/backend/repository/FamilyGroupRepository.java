package ru.tmobile.backend.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.tmobile.backend.entity.FamilyGroupEntity;

public interface FamilyGroupRepository extends JpaRepository<FamilyGroupEntity, Long> {
}
