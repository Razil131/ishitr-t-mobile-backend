package ru.tmobile.backend.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.tmobile.backend.entity.ServicePackageEntity;

public interface ServicePackageRepository extends JpaRepository<ServicePackageEntity, Long> {
}
