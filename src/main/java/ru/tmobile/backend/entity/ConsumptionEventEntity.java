package ru.tmobile.backend.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import ru.tmobile.backend.entity.enums.ResourceType;

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

    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "resource_type", nullable = false, columnDefinition = "E_RESOURCE_TYPE")
    private ResourceType resourceType;

    @Column(name = "amount", nullable = false)
    private Double amount;

    @Column(name = "event_timestamp", nullable = false)
    private OffsetDateTime eventTimestamp;

    @Column(name = "processed_at")
    private OffsetDateTime processedAt;

}