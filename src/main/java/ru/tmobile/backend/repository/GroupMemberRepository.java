package ru.tmobile.backend.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.tmobile.backend.entity.GroupMemberEntity;

import java.util.List;

public interface GroupMemberRepository extends JpaRepository<GroupMemberEntity, Long> {
    List<GroupMemberEntity> findByGroupId(Long groupId);

    @Query(value = "SELECT * FROM group_members WHERE status = CAST(:status AS e_member_status)", nativeQuery = true)
    List<GroupMemberEntity> findByStatus(@Param("status") String status);
}
