package ru.tmobile.backend.service;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import ru.tmobile.backend.entity.AuditLogEntity;
import ru.tmobile.backend.entity.FamilyGroupEntity;
import ru.tmobile.backend.entity.GroupMemberEntity;
import ru.tmobile.backend.entity.UserEntity;
import ru.tmobile.backend.entity.AuditLogEntity.ActionType;
import ru.tmobile.backend.entity.FamilyGroupEntity.GroupStatus;
import ru.tmobile.backend.entity.GroupMemberEntity.MemberRole;
import ru.tmobile.backend.entity.GroupMemberEntity.MemberStatus;
import ru.tmobile.backend.exception.BusinessRuleViolationException;
import ru.tmobile.backend.repository.AuditLogRepository;
import ru.tmobile.backend.repository.FamilyGroupRepository;
import ru.tmobile.backend.repository.GroupMemberRepository;
import ru.tmobile.backend.repository.UserRepository;
import ru.tmobile.backend.virtual_clock.VirtualClockService;

@Service
@RequiredArgsConstructor
public class FamilyGroupService {
    private final AuditLogRepository auditLogRepository;
    private final VirtualClockService virtualClock;
    private final UserRepository userRepository;
    private final FamilyGroupRepository familyGroupRepository;
    private final GroupMemberRepository groupMemberRepository;

    @Transactional 
    public FamilyGroupEntity createGroup(Long ownerId, List<Long> memberIds){
        validateCreateGroupRequest(ownerId,memberIds);
        OffsetDateTime now = virtualClock.instant().atZone(ZoneId.systemDefault()).toOffsetDateTime();
        FamilyGroupEntity newGroup = new FamilyGroupEntity();
        newGroup.setCreatedAt(now);
        newGroup.setOwnerId(ownerId);
        newGroup.setStatus(GroupStatus.ACTIVE);
        familyGroupRepository.save(newGroup);
        
        GroupMemberEntity ownerGroupMember = createMember(newGroup.getId(), ownerId, MemberRole.OWNER, now);
        List<GroupMemberEntity> groupMembers = new ArrayList<>();
        groupMembers.add(ownerGroupMember);

        for (Long memberId:memberIds){
            GroupMemberEntity groupMember = createMember(newGroup.getId(), memberId, MemberRole.MEMBER, now);
            groupMembers.add(groupMember);
        }

        groupMemberRepository.saveAll(groupMembers);

        AuditLogEntity ownerAuditLogEntity = createAuditLog(newGroup.getId(), ownerId, ownerId, ActionType.ADD_MEMBER, now, "{\"role\":\"OWNER\"}");
        List<AuditLogEntity> auditLogs = new ArrayList<>();
        auditLogs.add(ownerAuditLogEntity);
        for (Long memberId:memberIds){
            AuditLogEntity auditLogEntity = createAuditLog(newGroup.getId(), ownerId, memberId, ActionType.ADD_MEMBER, now, "{\"role\":\"MEMBER\"}");
            auditLogs.add(auditLogEntity);
        }
        auditLogRepository.saveAll(auditLogs);

        return newGroup;
    }

    private void validateCreateGroupRequest(Long ownerId, List<Long> memberIds){
        List<Long> allUserIds = new ArrayList<>(memberIds);
        allUserIds.add(ownerId);

        if (memberIds.size() < 1 || memberIds.size() > 3){
            throw new BusinessRuleViolationException(
                "INVALID_MEMBER_COUNT",
                "Users in group " + memberIds.size(),
                HttpStatus.BAD_REQUEST
            );
        }
        if (memberIds.contains(ownerId)){
            throw new BusinessRuleViolationException(
                "INVALID_REQUEST_OWNER_IN_MEMBERS",
                "Owner userId " + ownerId,
                HttpStatus.BAD_REQUEST
            );
        }
        List<GroupMemberEntity> existingMemberships = groupMemberRepository.findByUserIdInAndStatus(allUserIds, MemberStatus.ACTIVE);
        if (!existingMemberships.isEmpty()) {
            List<Long> blockedUserIds = existingMemberships.stream()
                .map(GroupMemberEntity::getUserId)
                .toList();
            throw new BusinessRuleViolationException(
                "BR_01_USER_ALREADY_IN_GROUP",
                "Users already in active group: " + blockedUserIds,
                HttpStatus.CONFLICT
            );
        }

        List<UserEntity> existingUsers = userRepository.findByIdIn(allUserIds);

        List<Long> existingUserIds = existingUsers.stream()
            .map(UserEntity::getId)
            .toList();

        List<Long> notFoundUserIds = allUserIds.stream()
            .filter(id -> !existingUserIds.contains(id))
            .toList();
            
        if (!notFoundUserIds.isEmpty()) {
            throw new BusinessRuleViolationException(
                    "USER_NOT_FOUND",
                    "Users not found: " + notFoundUserIds,
                    HttpStatus.NOT_FOUND
            );
        }
    }

    private GroupMemberEntity createMember(Long groupId, Long userId, MemberRole role, OffsetDateTime now){
        GroupMemberEntity groupMember = new GroupMemberEntity();
        groupMember.setGroupId(groupId);
        groupMember.setUserId(userId);
        groupMember.setRole(role);
        groupMember.setJoinedAt(now);
        groupMember.setStatus(MemberStatus.ACTIVE);
        return groupMember;
    }

    private AuditLogEntity createAuditLog(Long groupId, Long actorId, Long targetId, ActionType action, OffsetDateTime now, String details){
        AuditLogEntity auditLogEntity = new AuditLogEntity();
        auditLogEntity.setGroupId(groupId);
        auditLogEntity.setActorUserId(actorId);
        auditLogEntity.setActionType(action);
        auditLogEntity.setCreatedAt(now);
        auditLogEntity.setDetails(details);
        auditLogEntity.setTargetUserId(targetId);
        return auditLogEntity;
    }

    @Transactional 
    public void addMember(Long groupId, Long userId){
        FamilyGroupEntity group = familyGroupRepository.findByIdAndStatus(groupId, GroupStatus.ACTIVE)
            .orElseThrow(() -> new BusinessRuleViolationException("GROUP_NOT_FOUND", "Group not found: " + groupId, HttpStatus.NOT_FOUND));
        validateAddMemberRequest(group,userId);
        OffsetDateTime now = virtualClock.instant().atZone(ZoneId.systemDefault()).toOffsetDateTime();
        Long ownerId = group.getOwnerId();
        GroupMemberEntity groupMember = createMember(groupId, userId, MemberRole.MEMBER, now);
        groupMemberRepository.save(groupMember);
        AuditLogEntity auditLogEntity = createAuditLog(groupId, ownerId, userId, ActionType.ADD_MEMBER,now , "{\"role\":\"MEMBER\"}");
        auditLogRepository.save(auditLogEntity);
    }

    private void validateAddMemberRequest(FamilyGroupEntity group, Long userId){
        Long groupId = group.getId();
        // TODO добавить проверку того что вызывающий владелец группы

        if (userRepository.findById(userId).isEmpty()){
            throw new BusinessRuleViolationException(
                "USER_NOT_FOUND",
                "User not found: " + userId,
                HttpStatus.NOT_FOUND
            );
        }

        if (groupMemberRepository.findByUserIdAndStatus(userId, MemberStatus.ACTIVE).isPresent()){
            throw new BusinessRuleViolationException(
                "BR_01_USER_ALREADY_IN_GROUP",
                "User already in active group: " + userId,
                HttpStatus.CONFLICT
            );
        }

        if (groupMemberRepository.countByGroupIdAndStatus(groupId, MemberStatus.ACTIVE) >= 4){
            throw new BusinessRuleViolationException(
                "BR_11_GROUP_IS_FULL",
                "Group is full: " + groupId,
                HttpStatus.CONFLICT
            );
        }
    }
    
    @Transactional 
    public void removeMember(Long groupId, Long targetUserId){
        FamilyGroupEntity group = familyGroupRepository.findByIdAndStatus(groupId, GroupStatus.ACTIVE)
            .orElseThrow(() -> new BusinessRuleViolationException("GROUP_NOT_FOUND", "Group not found: " + groupId, HttpStatus.NOT_FOUND));
        Long ownerId = group.getOwnerId();
        if (targetUserId.equals(ownerId)){
            throw new BusinessRuleViolationException(
                "BR_22_REMOVE_OWNER",
                "You cant remove owner: " + ownerId,
                HttpStatus.BAD_REQUEST
            );
        }

        // TODO добавить проверку того что вызывающий владелец группы

        GroupMemberEntity targetUser = groupMemberRepository.findByGroupIdAndUserIdAndStatus(groupId,targetUserId,MemberStatus.ACTIVE)
            .orElseThrow(() -> new BusinessRuleViolationException("USER_NOT_IN_TARGET_GROUP","user is not in the target group: " + groupId,HttpStatus.NOT_FOUND));
        
        targetUser.setStatus(MemberStatus.EXCLUDED);
        groupMemberRepository.save(targetUser);

        OffsetDateTime now = virtualClock.instant().atZone(ZoneId.systemDefault()).toOffsetDateTime();
        AuditLogEntity auditLog = createAuditLog(groupId, ownerId, targetUserId, ActionType.REMOVE_MEMBER, now, "{\"previous_role\":\"MEMBER\"}");
        auditLogRepository.save(auditLog);
        
    }

    @Transactional 
    public void leaveGroup(Long groupId, Long userId){
        FamilyGroupEntity group = familyGroupRepository.findByIdAndStatus(groupId, GroupStatus.ACTIVE)
            .orElseThrow(() -> new BusinessRuleViolationException("GROUP_NOT_FOUND", "Group not found: " + groupId, HttpStatus.NOT_FOUND));
        Long ownerId = group.getOwnerId();
        if (userId.equals(ownerId)){
            throw new BusinessRuleViolationException(
                "BR_23_OWNER_LEAVE",
                "Owner can't leave: " + ownerId,
                HttpStatus.BAD_REQUEST
            );
        }

        // TODO добавить проверку того что вызывающий пользователь с userId

        GroupMemberEntity user = groupMemberRepository.findByGroupIdAndUserIdAndStatus(groupId,userId,MemberStatus.ACTIVE)
            .orElseThrow(() -> new BusinessRuleViolationException("USER_NOT_IN_TARGET_GROUP","user is not in the target group: " + groupId,HttpStatus.NOT_FOUND));
        
        user.setStatus(MemberStatus.LEAVE);
        groupMemberRepository.save(user);

        OffsetDateTime now = virtualClock.instant().atZone(ZoneId.systemDefault()).toOffsetDateTime();
        AuditLogEntity auditLog = createAuditLog(groupId, userId, userId, ActionType.MEMBER_LEFT, now, "{\"reason\":\"voluntary\"}");
        auditLogRepository.save(auditLog);

    }

    @Transactional 
    public void transferOwnership(Long groupId, Long newOwnerId){
        FamilyGroupEntity group = familyGroupRepository.findByIdAndStatus(groupId, GroupStatus.ACTIVE)
            .orElseThrow(() -> new BusinessRuleViolationException("GROUP_NOT_FOUND", "Group not found: " + groupId, HttpStatus.NOT_FOUND));
        Long oldOwnerId = group.getOwnerId();

        if (newOwnerId.equals(oldOwnerId)){
            throw new BusinessRuleViolationException(
                "BR_25_SAME_OWNER",
                "Owner can't transfer ownership to himself: " + oldOwnerId,
                HttpStatus.BAD_REQUEST
            );
        }
        GroupMemberEntity oldOwner = groupMemberRepository.findByGroupIdAndUserIdAndStatus(groupId,oldOwnerId,MemberStatus.ACTIVE)
            .orElseThrow(() -> new BusinessRuleViolationException("USER_NOT_FOUND", "Old owner not found: " + oldOwnerId, HttpStatus.NOT_FOUND));

        GroupMemberEntity newOwner = groupMemberRepository.findByGroupIdAndUserIdAndStatus(groupId,newOwnerId,MemberStatus.ACTIVE)
            .orElseThrow(() -> new BusinessRuleViolationException("BR_24_NEW_OWNER_NOT_MEMBER","user is not in the target group: " + groupId,HttpStatus.BAD_REQUEST));

        // TODO добавить проверку того что вызывающий владелец группы

        OffsetDateTime now = virtualClock.instant().atZone(ZoneId.systemDefault()).toOffsetDateTime();
        oldOwner.setRole(MemberRole.MEMBER);
        newOwner.setRole(MemberRole.OWNER);
        group.setOwnerId(newOwnerId);
        groupMemberRepository.saveAll(List.of(oldOwner, newOwner));
        familyGroupRepository.save(group);

        AuditLogEntity auditLog = createAuditLog(groupId, oldOwnerId, newOwnerId, ActionType.OWNERSHIP_TRANSFERRED, now, String.format("{\"previous_owner_id\":%d,\"new_owner_id\":%d}", oldOwnerId, newOwnerId));
        auditLogRepository.save(auditLog);
        
    }

    @Transactional 
    public void disbandGroup(Long groupId){
        FamilyGroupEntity group = familyGroupRepository.findByIdAndStatus(groupId, GroupStatus.ACTIVE)
            .orElseThrow(() -> new BusinessRuleViolationException("GROUP_NOT_FOUND", "Group not found: " + groupId, HttpStatus.NOT_FOUND));
        // TODO добавить проверку того что вызывающий владелец группы
        OffsetDateTime now = virtualClock.instant().atZone(ZoneId.systemDefault()).toOffsetDateTime();
        Long ownerId = group.getOwnerId();

        List<GroupMemberEntity> members = groupMemberRepository.findByGroupIdAndStatus(groupId, MemberStatus.ACTIVE);
        for (GroupMemberEntity member : members){
            member.setStatus(MemberStatus.EXCLUDED);
        }
        groupMemberRepository.saveAll(members);

        group.setStatus(GroupStatus.DISBANDED);
        familyGroupRepository.save(group);

        AuditLogEntity auditLog = createAuditLog(groupId, ownerId, ownerId, ActionType.DISBAND_GROUP, now, String.format("{\"members_count_at_disband\":%d}", members.size()));
        auditLogRepository.save(auditLog);
    }
}
