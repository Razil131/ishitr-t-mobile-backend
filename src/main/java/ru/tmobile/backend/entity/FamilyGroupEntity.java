package ru.tmobile.backend.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;

@Entity
@Table(name = "family_groups")
@Getter @Setter
public class FamilyGroupEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "owner_id", nullable = false)
    private Long ownerId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private GroupStatus status;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    public enum GroupStatus {
        ACTIVE, DISBANDED
    }
}
