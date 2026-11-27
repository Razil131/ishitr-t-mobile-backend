package ru.tmobile.backend.service;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import ru.tmobile.backend.dto.familyGroup.TransferPendingInfo;
import ru.tmobile.backend.dto.familyGroup.TransferPendingResponse;
import ru.tmobile.backend.entity.AuditLogEntity.ActionType;
import ru.tmobile.backend.entity.FamilyGroupEntity;
import ru.tmobile.backend.entity.FamilyGroupEntity.GroupStatus;
import ru.tmobile.backend.entity.GroupMemberEntity;
import ru.tmobile.backend.entity.GroupMemberEntity.MemberRole;
import ru.tmobile.backend.entity.GroupMemberEntity.MemberStatus;
import ru.tmobile.backend.entity.UserEntity;
import ru.tmobile.backend.exception.AuthenticationException;
import ru.tmobile.backend.exception.AuthorizationException;
import ru.tmobile.backend.exception.BusinessRuleViolationException;
import ru.tmobile.backend.repository.FamilyGroupRepository;
import ru.tmobile.backend.repository.GroupMemberRepository;
import ru.tmobile.backend.repository.GroupPackageBalanceRepository;
import ru.tmobile.backend.repository.UserRepository;
import ru.tmobile.backend.virtual_clock.VirtualClockService;

@Service
@RequiredArgsConstructor
public class FamilyGroupService {
    private final GroupPackageBalanceRepository groupPackageBalanceRepository;
    private final GroupAuditService auditService;
    private final UserLimitService userLimitService;
    private final VirtualClockService virtualClock;
    private final UserRepository userRepository;
    private final FamilyGroupRepository familyGroupRepository;
    private final GroupMemberRepository groupMemberRepository;

    private static final java.time.YearMonth BASE_PERIOD = java.time.YearMonth.of(2026,1);

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

        var ownerAudit = auditService.log(newGroup.getId(), ownerId, ownerId, ActionType.ADD_MEMBER, now, Map.of("role","OWNER"));
        List<ru.tmobile.backend.entity.AuditLogEntity> auditLogs = new ArrayList<>();
        auditLogs.add(ownerAudit);
        for (Long memberId:memberIds){
            var audit = auditService.log(newGroup.getId(), ownerId, memberId, ActionType.ADD_MEMBER, now, Map.of("role","MEMBER"));
            auditLogs.add(audit);
        }
        auditService.saveAll(auditLogs);

        return newGroup;
    }

    private void validateCreateGroupRequest(Long ownerId, List<Long> memberIds){
        if (memberIds.size() < 1 || memberIds.size() > 3){
            throw new BusinessRuleViolationException(
                "INVALID_MEMBER_COUNT",
                "Users in group " + memberIds.size(),
                HttpStatus.BAD_REQUEST
            );
        }

        if (memberIds.stream().distinct().count() != memberIds.size()) {
            throw new BusinessRuleViolationException(
                "DUPLICATE_MEMBERS_IDS",
                "Duplicate member ids: " + memberIds,
                HttpStatus.BAD_REQUEST
            );
        }

        List<Long> allUserIds = new ArrayList<>(memberIds);
        allUserIds.add(ownerId);

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

    @Transactional
    public void addMember(Long groupId, Long userId, Long callerId){
        FamilyGroupEntity group = familyGroupRepository.findByIdAndStatus(groupId, GroupStatus.ACTIVE)
            .orElseThrow(() -> new BusinessRuleViolationException("GROUP_NOT_FOUND", "Group not found: " + groupId, HttpStatus.NOT_FOUND));
        validateOwnerAccess(group, callerId);
        validateAddMemberRequest(group,userId);
        OffsetDateTime now = virtualClock.instant().atZone(ZoneId.systemDefault()).toOffsetDateTime();
        Long ownerId = group.getOwnerId();
        GroupMemberEntity groupMember = createMember(groupId, userId, MemberRole.MEMBER, now);
        groupMemberRepository.save(groupMember);
        var audit = auditService.log(groupId, ownerId, userId, ActionType.ADD_MEMBER, now, Map.of("role","MEMBER"));
        auditService.save(audit);
    }

    private void validateOwnerAccess(FamilyGroupEntity group, Long callerId) {
        if (!group.getOwnerId().equals(callerId)) {
            throw new AuthorizationException("FORBIDDEN", "Only group owner can perform this action");
        }
    }

    private void validateAddMemberRequest(FamilyGroupEntity group, Long userId){
        Long groupId = group.getId();

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
    public void removeMember(Long groupId, Long targetUserId, Long callerId){
        FamilyGroupEntity group = familyGroupRepository.findByIdAndStatus(groupId, GroupStatus.ACTIVE)
            .orElseThrow(() -> new BusinessRuleViolationException("GROUP_NOT_FOUND", "Group not found: " + groupId, HttpStatus.NOT_FOUND));
        validateOwnerAccess(group, callerId);
        Long ownerId = group.getOwnerId();
        if (targetUserId.equals(ownerId)){
            throw new BusinessRuleViolationException(
                "BR_22_REMOVE_OWNER",
                "You cant remove owner: " + ownerId,
                HttpStatus.BAD_REQUEST
            );
        }

        GroupMemberEntity targetUser = groupMemberRepository.findByGroupIdAndUserIdAndStatus(groupId,targetUserId,MemberStatus.ACTIVE)
            .orElseThrow(() -> new BusinessRuleViolationException("USER_NOT_IN_TARGET_GROUP","user is not in the target group: " + groupId,HttpStatus.NOT_FOUND));

        targetUser.setStatus(MemberStatus.EXCLUDED);
        groupMemberRepository.save(targetUser);
        userLimitService.invalidateMemberLimits(groupId, targetUserId);

        OffsetDateTime now = virtualClock.instant().atZone(ZoneId.systemDefault()).toOffsetDateTime();
        var audit = auditService.log(groupId, ownerId, targetUserId, ActionType.MEMBER_EXCLUDED, now, Map.of("previous_role","MEMBER"));
        auditService.save(audit);

    }

    @Transactional
    public void excludeMember(Long groupId, Long targetUserId, Long callerId) {
        FamilyGroupEntity group = familyGroupRepository.findByIdAndStatus(groupId, GroupStatus.ACTIVE)
            .orElseThrow(() -> new BusinessRuleViolationException(
                "GROUP_NOT_FOUND", "Group not found" + groupId, HttpStatus.NOT_FOUND));;
        validateOwnerAccess(group, callerId);

        if (targetUserId.equals(group.getOwnerId())) {
            throw new BusinessRuleViolationException(
                "CANNOT_EXCLUDE_SELF", "Owner cannot exclude himself", HttpStatus.CONFLICT);
        }

        GroupMemberEntity target = groupMemberRepository
            .findByGroupIdAndUserIdAndStatus(groupId, targetUserId, MemberStatus.ACTIVE)
            .orElseThrow(() -> new BusinessRuleViolationException(
                "MEMBER_NOT_ACTIVE", "Target member is not active", HttpStatus.CONFLICT));
            
        target.setStatus(MemberStatus.EXCLUDED);
        groupMemberRepository.save(target);
        userLimitService.invalidateMemberLimits(groupId, targetUserId);

        OffsetDateTime now = virtualClock.instant().atZone(ZoneId.systemDefault()).toOffsetDateTime();
        var audit = auditService.log(groupId, callerId, targetUserId, ActionType.MEMBER_EXCLUDED, now,
            Map.of("previous_role", target.getRole().name()));
        auditService.save(audit);
    }

    @Transactional
    public void leaveGroup(Long groupId, Long callerId) {
        if (callerId == null) {
            throw new AuthenticationException("UNAUTHENTICATED", "User not authenticated");
        }

        FamilyGroupEntity group = familyGroupRepository.findByIdAndStatus(groupId, GroupStatus.ACTIVE)
            .orElseThrow(() -> new BusinessRuleViolationException(
                "GROUP_NOT_FOUND", "Group not found: " + groupId, HttpStatus.NOT_FOUND));

        if (callerId.equals(group.getOwnerId())) {
            throw new BusinessRuleViolationException(
                "OWNER_CANNOT_LEAVE", "Owner cannot leave group", HttpStatus.CONFLICT);
        }

        GroupMemberEntity member = groupMemberRepository
            .findByGroupIdAndUserIdAndStatus(groupId, callerId, MemberStatus.ACTIVE)
            .orElseThrow(() -> new BusinessRuleViolationException(
                "MEMBER_NOT_ACTIVE", "Member is not active", HttpStatus.CONFLICT));

        member.setStatus(MemberStatus.LEAVE);
        groupMemberRepository.save(member);
        userLimitService.invalidateMemberLimits(groupId, callerId);

        OffsetDateTime now = virtualClock.instant().atZone(ZoneId.systemDefault()).toOffsetDateTime();
        var audit = auditService.log(groupId, callerId, callerId, ActionType.MEMBER_LEFT, now,
            Map.of("reason", "voluntary"));
        auditService.save(audit);
    }

    @Transactional
    public TransferPendingResponse initiateTransfer(Long groupId, Long newOwnerId, Long callerId){
        FamilyGroupEntity group = familyGroupRepository.findByIdAndStatus(groupId, GroupStatus.ACTIVE)
            .orElseThrow(() -> new BusinessRuleViolationException("GROUP_NOT_FOUND", "Group not found: " + groupId, HttpStatus.NOT_FOUND));
        validateOwnerAccess(group, callerId);

        if (group.getPendingTransfer() != null) {
            throw new BusinessRuleViolationException(
                "TRANSFER_ALREADY_PENDING", "Transfer already pending", HttpStatus.CONFLICT);
        }

        if (newOwnerId.equals(group.getOwnerId())) {
            throw new BusinessRuleViolationException("TARGET_IS_OWNER", "Cannot transfer to current owner", HttpStatus.CONFLICT);
        }

        groupMemberRepository
            .findByGroupIdAndUserIdAndStatus(groupId, newOwnerId, MemberStatus.ACTIVE)
            .orElseThrow(() -> new BusinessRuleViolationException(
                "MEMBER_NOT_ACTIVE", "Target member is not active", HttpStatus.CONFLICT));


        OffsetDateTime now = virtualClock.instant().atZone(ZoneId.systemDefault()).toOffsetDateTime();
        TransferPendingInfo pending = new TransferPendingInfo(group.getOwnerId(), newOwnerId, now);
        group.setPendingTransfer(pending);
        familyGroupRepository.save(group);

        return new TransferPendingResponse(groupId, pending);
    }

    @Transactional 
    public FamilyGroupEntity acceptTransfer(Long groupId, Long callerId) {
        FamilyGroupEntity group = familyGroupRepository.findByIdAndStatus(groupId, GroupStatus.ACTIVE)
            .orElseThrow(() -> new BusinessRuleViolationException(
                "GROUP_NOT_FOUND", "Group not found: " + groupId, HttpStatus.NOT_FOUND));
            
        TransferPendingInfo pending = group.getPendingTransfer();
        if (pending == null) {
            throw new BusinessRuleViolationException("TRANSFER_NOT_PENDING", "No pending transfer", HttpStatus.CONFLICT);
        }

        if (!pending.toUserId().equals(callerId)) {
            throw new AuthorizationException("FORBIDDEN", "Only target user can accept transfer");
        }

        Long oldOwnerId = group.getOwnerId();
        Long newOwnerId = pending.toUserId();

        GroupMemberEntity oldOwner = groupMemberRepository.findByGroupIdAndUserIdAndStatus(groupId,oldOwnerId,MemberStatus.ACTIVE)
            .orElseThrow(() -> new BusinessRuleViolationException("USER_NOT_FOUND", "Old owner not found: " + oldOwnerId, HttpStatus.NOT_FOUND));

        GroupMemberEntity newOwner = groupMemberRepository.findByGroupIdAndUserIdAndStatus(groupId,newOwnerId,MemberStatus.ACTIVE)
            .orElseThrow(() -> new BusinessRuleViolationException("USER_NOT_FOUND", "New owner not found", HttpStatus.NOT_FOUND));

        oldOwner.setRole(MemberRole.MEMBER);
        newOwner.setRole(MemberRole.OWNER);
        group.setOwnerId(newOwnerId);
        group.setPendingTransfer(null);
        groupMemberRepository.saveAll(List.of(oldOwner, newOwner));
        familyGroupRepository.save(group);

        OffsetDateTime now = virtualClock.instant().atZone(ZoneId.systemDefault()).toOffsetDateTime();
        var audit = auditService.log(groupId, oldOwnerId, newOwnerId, ActionType.OWNERSHIP_TRANSFERRED, now,
            Map.of("previous_owner_id", oldOwnerId, "new_owner_id", newOwnerId));
        auditService.save(audit);
        
        return group;
    }

    @Transactional
    public void disbandGroup(Long groupId, Long callerId){
        FamilyGroupEntity group = familyGroupRepository.findByIdAndStatus(groupId, GroupStatus.ACTIVE)
            .orElseThrow(() -> new BusinessRuleViolationException("GROUP_NOT_FOUND", "Group not found: " + groupId, HttpStatus.NOT_FOUND));
        validateOwnerAccess(group, callerId);
        OffsetDateTime now = virtualClock.instant().atZone(ZoneId.systemDefault()).toOffsetDateTime();
        Long ownerId = group.getOwnerId();

        List<GroupMemberEntity> members = groupMemberRepository.findByGroupIdAndStatus(groupId, MemberStatus.ACTIVE);
        for (GroupMemberEntity member : members){
            member.setStatus(MemberStatus.EXCLUDED);
            userLimitService.invalidateMemberLimits(groupId, member.getUserId());
        }
        groupMemberRepository.saveAll(members);

        Long periodNumber = java.time.temporal.ChronoUnit.MONTHS.between(BASE_PERIOD, java.time.YearMonth.from(now)) +1;

        groupPackageBalanceRepository.findByGroupIdAndPeriodIdForUpdate(groupId, periodNumber)
            .ifPresent(balance->{balance.setRemainingMinutes(0L);
                                    balance.setRemainingTraffic(0.0);
                                    groupPackageBalanceRepository.save(balance);
                                });

        group.setStatus(GroupStatus.DISBANDED);
        familyGroupRepository.save(group);

        var audit = auditService.log(groupId, ownerId, ownerId, ActionType.DISBAND_GROUP, now,
            Map.of("members_count_at_disband", members.size()));
        auditService.save(audit);
    }
}
