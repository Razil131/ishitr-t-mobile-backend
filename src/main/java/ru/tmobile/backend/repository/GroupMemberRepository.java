package ru.tmobile.backend.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.tmobile.backend.entity.GroupMemberEntity;
import ru.tmobile.backend.entity.GroupMemberEntity.MemberRole;
import ru.tmobile.backend.entity.GroupMemberEntity.MemberStatus;


public interface GroupMemberRepository extends JpaRepository<GroupMemberEntity, Long> {
    Optional<GroupMemberEntity> findByUserIdAndStatus(Long userId, MemberStatus status);
    List<GroupMemberEntity> findByGroupIdAndStatus(Long groupId, MemberStatus status);
    List<GroupMemberEntity> findByStatus(MemberStatus status);

    @Query("SELECT gm.userId FROM GroupMemberEntity gm WHERE gm.status = :status")
    List<Long> findUserIdsByStatus(@Param("status") MemberStatus status);
    Optional<GroupMemberEntity> findByGroupIdAndRoleAndStatus(Long groupId, MemberRole role, MemberStatus status);
    Long countByGroupIdAndStatus(Long groupId, MemberStatus status);
    List<GroupMemberEntity> findByUserIdInAndStatus(List<Long> userIds, MemberStatus status);
    Optional<GroupMemberEntity> findByGroupIdAndUserIdAndStatus(Long groupId, Long userId, MemberStatus status);
}
