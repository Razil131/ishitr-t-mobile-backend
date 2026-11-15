package ru.tmobile.backend.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;
import ru.tmobile.backend.entity.GroupPackageBalanceEntity;

import java.util.Optional;

public interface GroupPackageBalanceRepository extends JpaRepository<GroupPackageBalanceEntity, GroupPackageBalanceEntity.GroupPackageBalanceId> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT b FROM GroupPackageBalanceEntity b WHERE b.groupId = :groupId AND b.periodId = :periodId")
    Optional<GroupPackageBalanceEntity> findByGroupIdAndPeriodIdForUpdate(@Param("groupId") Long groupId, @Param("periodId") Long periodId);
}