package ru.tmobile.backend.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;

@Entity 
@Table(name = "user_sessions")
@Getter @Setter
public class UserSessionEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;
    
    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "access_token", nullable = false, unique = true, length = 255)
    private String accessToken;
    
    @Column(name = "refresh_token", nullable = false, unique = true, length = 255)
    private String refreshToken;

    @Column(name = "expires_at", nullable = false)
    private OffsetDateTime expiresAt;

    @Column(name = "revoked_at")
    private OffsetDateTime revokedAt;
}
