package ru.tmobile.backend.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;

@Entity
@Table(name = "consumption_events")
@Getter @Setter
public class ConsumptionEventEntity {

    @Id
    @Column(name = "event_id", length = 36)
    private String eventId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "group_id")
    private Long groupId;

    @Enumerated(EnumType.STRING)
    @Column(name = "resource_type", nullable = false)
    private ResourceType resourceType;

    @Column(nullable = false)
    private Double amount;

    @Column(name = "event_timestamp", nullable = false)
    private OffsetDateTime eventTimestamp;

    @Column(name = "is_anomaly", nullable = false)
    private Boolean isAnomaly = false;

    @Column(name = "processed_at")
    private OffsetDateTime processedAt;

    public enum ResourceType {
        TRAFFIC, MINUTES
    }
}