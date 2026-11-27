package ru.tmobile.backend.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.fasterxml.jackson.databind.ObjectMapper;

import ru.tmobile.backend.entity.AuditLogEntity;
import ru.tmobile.backend.entity.AuditLogEntity.ActionType;
import ru.tmobile.backend.repository.AuditLogRepository;

@ExtendWith(MockitoExtension.class)
class GroupAuditServiceTest {
    @Mock AuditLogRepository auditLogRepository;

    GroupAuditService svc;
    OffsetDateTime now;

    @BeforeEach
    void setUp() {
        svc = new GroupAuditService(auditLogRepository, new ObjectMapper());
        now = OffsetDateTime.of(2026, 1, 15, 12, 0, 0, 0, ZoneOffset.UTC);
    }

    @Test
    void logBuildsAuditEntityWithFieldsAndJsonDetails() {
        Map<String, Object> details = Map.of(
            "target_user_id", 5L,
            "resource_type", "TRAFFIC",
            "limit_value", 100.0);

        AuditLogEntity audit = svc.log(10L, 1L, 5L, ActionType.LIMIT_CHANGED, now, details);

        assertEquals(10L, audit.getGroupId());
        assertEquals(1L, audit.getActorUserId());
        assertEquals(5L, audit.getTargetUserId());
        assertEquals(ActionType.LIMIT_CHANGED, audit.getActionType());
        assertEquals(now, audit.getCreatedAt());
        assertTrue(audit.getDetails().contains("\"target_user_id\":5"));
        assertTrue(audit.getDetails().contains("\"resource_type\":\"TRAFFIC\""));
        assertTrue(audit.getDetails().contains("\"limit_value\":100.0"));
    }

    @Test
    void logUsesEmptyJsonObjectForNullDetails() {
        AuditLogEntity audit = svc.log(10L, 1L, 5L, ActionType.MEMBER_LEFT, now, null);
        assertEquals("{}", audit.getDetails());
    }

    @Test
    void logUsesEmptyJsonObjectForEmptyDetails() {
        AuditLogEntity audit = svc.log(10L, 1L, 5L, ActionType.MEMBER_LEFT, now, Map.of());
        assertEquals("{}", audit.getDetails());
    }

    @Test
    void saveDelegatesToRepository() {
        AuditLogEntity audit = svc.log(10L, 1L, 5L, ActionType.MEMBER_LEFT, now, Map.of());

        svc.save(audit);

        verify(auditLogRepository).save(audit);
    }

    @Test
    @SuppressWarnings("unchecked")
    void saveAllDelegatesToRepository() {
        AuditLogEntity first = svc.log(10L, 1L, 1L, ActionType.MEMBER_ADDED, now, Map.of("role", "OWNER"));
        AuditLogEntity second = svc.log(10L, 1L, 2L, ActionType.MEMBER_ADDED, now, Map.of("role", "MEMBER"));
        List<AuditLogEntity> audits = List.of(first, second);

        svc.saveAll(audits);

        ArgumentCaptor<Iterable<AuditLogEntity>> cap = ArgumentCaptor.forClass(Iterable.class);
        verify(auditLogRepository).saveAll(cap.capture());
        assertEquals(2, (int) java.util.stream.StreamSupport.stream(cap.getValue().spliterator(), false).count());
    }
}
