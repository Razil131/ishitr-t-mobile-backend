package ru.tmobile.backend.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "auth_codes")
@Getter @Setter
public class AuthCodeEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "session_id", unique = true)
    private UUID sessionId;

    @Column(name = "code", nullable = false, length = 10)
    private String code;

    @Column(name = "expires_at", nullable = false)
    private OffsetDateTime expiresAt;
    
    @Column(name = "is_used", nullable = false)
    private boolean isUsed;
    
    @Column(name = "attempts_count", nullable = false)
    private int attemptsCount;
    
    @Column(name = "blocked_until")
    private OffsetDateTime blockedUntil;
    
    @Column(name = "requested_at")
    private OffsetDateTime requestedAt;
}
