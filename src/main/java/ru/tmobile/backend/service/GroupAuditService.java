package ru.tmobile.backend.service;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;
import ru.tmobile.backend.entity.AuditLogEntity;
import ru.tmobile.backend.entity.AuditLogEntity.ActionType;
import ru.tmobile.backend.repository.AuditLogRepository;

@Service
@RequiredArgsConstructor
public class GroupAuditService {
    private final AuditLogRepository auditLogRepository;
    private final ObjectMapper objectMapper;

    public AuditLogEntity log(Long groupId, Long actorId, Long targetId, ActionType action, OffsetDateTime now, Map<String, Object> details) {
        AuditLogEntity audit = new AuditLogEntity();
        audit.setGroupId(groupId);
        audit.setActorUserId(actorId);
        audit.setTargetUserId(targetId);
        audit.setActionType(action);
        audit.setCreatedAt(now);
        audit.setDetails(toJson(details));
        return audit;
    }

    public void save(AuditLogEntity audit) {
        auditLogRepository.save(audit);
    }

    public void saveAll(List<AuditLogEntity> audits) {
        auditLogRepository.saveAll(audits);
    }

    private String toJson(Map<String, Object> details) {
        if (details == null || details.isEmpty()) {
            return "{}";
        }
        try {
            return objectMapper.writeValueAsString(details);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize audit details", e);
        }
    }
}
