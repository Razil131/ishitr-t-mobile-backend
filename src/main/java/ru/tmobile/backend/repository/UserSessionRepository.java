package ru.tmobile.backend.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import ru.tmobile.backend.entity.UserSessionEntity;

public interface UserSessionRepository extends JpaRepository<UserSessionEntity, Long>{
    Optional<UserSessionEntity> findByAccessTokenAndRevokedAtIsNull(String accessToken);
    Optional<UserSessionEntity> findByRefreshTokenAndRevokedAtIsNull(String refreshToken);
}
