package ru.tmobile.backend.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.tmobile.backend.entity.GroupSubscriptionEntity;

import java.time.OffsetDateTime;
import java.util.Optional;

public interface GroupSubscriptionRepository extends JpaRepository<GroupSubscriptionEntity, Long> {

    @Query("SELECT gs FROM GroupSubscriptionEntity gs WHERE gs.groupId = :groupId AND gs.periodStart <= :now AND gs.periodEnd >= :now")
    Optional<GroupSubscriptionEntity> findActiveByGroupId(@Param("groupId") Long groupId, @Param("now") OffsetDateTime now);
}