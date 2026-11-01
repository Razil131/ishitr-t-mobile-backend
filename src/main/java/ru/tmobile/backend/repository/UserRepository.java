package ru.tmobile.backend.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.tmobile.backend.entity.UserEntity;

public interface UserRepository extends JpaRepository<UserEntity, Long> {
}
