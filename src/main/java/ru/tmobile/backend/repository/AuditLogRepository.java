package ru.tmobile.backend.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import ru.tmobile.backend.entity.AuditLogEntity;
import java.util.List;


public interface AuditLogRepository extends JpaRepository<AuditLogEntity, Long> {
    List<AuditLogEntity> findByGroupIdOrderByCreatedAtDesc(Long groupId);
}
