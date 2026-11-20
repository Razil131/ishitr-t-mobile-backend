package ru.tmobile.backend.repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import ru.tmobile.backend.entity.AuthCodeEntity;


public interface AuthCodeRepository extends JpaRepository<AuthCodeEntity, Long> {
    Optional<AuthCodeEntity> findBySessionIdAndCode(UUID sessionId, String code);
    Optional<AuthCodeEntity> findBySessionId(UUID sessionId);
    Optional<AuthCodeEntity> findTopByUserIdAndIsUsedFalseOrderByRequestedAtDesc(Long userId);
    List<AuthCodeEntity> findAllByUserIdAndIsUsedFalseAndExpiresAtAfter(Long userId, OffsetDateTime now);
}
