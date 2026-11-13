package ru.tmobile.backend.entity;
import java.time.OffsetDateTime;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "audit_logs")
@Getter @Setter
public class AuditLogEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "group_id", nullable = false)
    private Long groupId;

    @Column(name = "actor_user_id", nullable = false)
    private Long actorUserId;

    @Enumerated(EnumType.STRING)
    @Column(name = "action", nullable = false)
    private ActionType action;

    @Column(name = "target_user_id")
    private Long targetUserId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload", columnDefinition = "jsonb")
    private String payload;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;


    public enum ActionType {
        ADD_MEMBER,
        REMOVE_MEMBER,
        CHANGE_LIMIT,
        TRANSFER_OWNERSHIP,
        DISBAND_GROUP,
        GROUP_CREATED,
        MEMBER_ADDED,
        MEMBER_EXCLUDED,
        MEMBER_LEFT,
        LIMIT_CHANGED,
        OWNERSHIP_TRANSFERRED,
        GROUP_DISBANDED
    }
}