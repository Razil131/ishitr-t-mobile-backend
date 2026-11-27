package ru.tmobile.backend.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import ru.tmobile.backend.entity.UserEntity;

public interface UserRepository extends JpaRepository<UserEntity, Long> {
    Optional<UserEntity> findById(Long id);
    List<UserEntity> findByIdIn(List<Long> ids);
    Optional<UserEntity> findByPhoneNumber(String phoneNumber);
    boolean existsByPhoneNumber(String phoneNumber);
    Optional<UserEntity> findByPhoneNumberIgnoreCase(String phoneNumber);
    List<UserEntity> findTop10ByPhoneNumberStartingWithIgnoreCase(String phonePrefix);
}
