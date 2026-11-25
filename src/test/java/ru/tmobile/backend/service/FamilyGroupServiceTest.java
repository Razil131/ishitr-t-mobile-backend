package ru.tmobile.backend.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import ru.tmobile.backend.entity.AuditLogEntity;
import ru.tmobile.backend.entity.AuditLogEntity.ActionType;
import ru.tmobile.backend.entity.FamilyGroupEntity;
import ru.tmobile.backend.entity.FamilyGroupEntity.GroupStatus;
import ru.tmobile.backend.entity.GroupMemberEntity;
import ru.tmobile.backend.entity.GroupMemberEntity.MemberRole;
import ru.tmobile.backend.entity.GroupMemberEntity.MemberStatus;
import ru.tmobile.backend.entity.GroupPackageBalanceEntity;
import ru.tmobile.backend.entity.UserEntity;
import ru.tmobile.backend.entity.UserLimitEntity;
import ru.tmobile.backend.entity.enums.ResourceType;
import ru.tmobile.backend.exception.BusinessRuleViolationException;
import ru.tmobile.backend.repository.AuditLogRepository;
import ru.tmobile.backend.repository.FamilyGroupRepository;
import ru.tmobile.backend.repository.GroupMemberRepository;
import ru.tmobile.backend.repository.GroupPackageBalanceRepository;
import ru.tmobile.backend.repository.UserLimitRepository;
import ru.tmobile.backend.repository.UserRepository;
import ru.tmobile.backend.virtual_clock.VirtualClockService;

@ExtendWith(MockitoExtension.class)
class FamilyGroupServiceTest {
    @Mock AuditLogRepository auditRepo;
    @Mock VirtualClockService clock;
    @Mock UserRepository userRepo;
    @Mock FamilyGroupRepository groupRepo;
    @Mock GroupMemberRepository memberRepo;
    @Mock UserLimitRepository limitRepo;
    @Mock GroupPackageBalanceRepository balanceRepo;
    FamilyGroupService svc;
    OffsetDateTime now;

    @BeforeEach void up() {
        svc = new FamilyGroupService(balanceRepo, limitRepo, auditRepo, clock, userRepo, groupRepo, memberRepo);
        now = OffsetDateTime.of(2026, 1, 15, 12, 0, 0, 0, ZoneOffset.UTC);
    }

    void setupClock() {
        when(clock.instant()).thenReturn(now.toInstant());
    }

    void setupCreateGroupStubs() {
        setupClock();
        when(groupRepo.save(any())).thenAnswer(i -> { FamilyGroupEntity g = i.getArgument(0); if (g.getId() == null) g.setId(100L); return g; });
        when(memberRepo.saveAll(anyList())).thenAnswer(i -> i.getArgument(0));
        when(auditRepo.saveAll(anyList())).thenAnswer(i -> i.getArgument(0));
    }

    void setupSingleSaveStubs() {
        setupClock();
        when(memberRepo.save(any())).thenAnswer(i -> i.getArgument(0));
        when(auditRepo.save(any())).thenAnswer(i -> i.getArgument(0));
        lenient().when(limitRepo.findByUserIdAndGroupIdAndResourceType(anyLong(), anyLong(), any(ResourceType.class)))
            .thenReturn(Optional.empty());
    }

    void setupTransferStubs() {
        setupClock();
        when(memberRepo.saveAll(anyList())).thenAnswer(i -> i.getArgument(0));
        when(groupRepo.save(any())).thenAnswer(i -> i.getArgument(0));
        when(auditRepo.save(any())).thenAnswer(i -> i.getArgument(0));
    }

    void setupDisbandStubs() {
        setupClock();
        when(memberRepo.saveAll(anyList())).thenAnswer(i -> i.getArgument(0));
        when(groupRepo.save(any())).thenAnswer(i -> i.getArgument(0));
        when(auditRepo.save(any())).thenAnswer(i -> i.getArgument(0));
    }

    void setupNoLimitsOrBalance() {
        lenient().when(limitRepo.findByUserIdAndGroupIdAndResourceType(anyLong(), anyLong(), any(ResourceType.class)))
            .thenReturn(Optional.empty());
        lenient().when(balanceRepo.findByGroupIdAndPeriodIdForUpdate(anyLong(), anyLong()))
            .thenReturn(Optional.empty());
    }

    FamilyGroupEntity group(long id, long ownerId, GroupStatus status) {
        FamilyGroupEntity g = new FamilyGroupEntity();
        g.setId(id); g.setOwnerId(ownerId); g.setStatus(status); g.setCreatedAt(now);
        return g;
    }
    GroupMemberEntity member(long gid, long uid, MemberRole role, MemberStatus status) {
        GroupMemberEntity m = new GroupMemberEntity();
        m.setId(1L); m.setGroupId(gid); m.setUserId(uid); m.setRole(role); m.setStatus(status); m.setJoinedAt(now);
        return m;
    }
    UserEntity user(long id) {
        UserEntity u = new UserEntity(); u.setId(id); u.setPhoneNumber("+7999000000" + id); u.setName("U" + id); u.setCreatedAt(now);
        return u;
    }
    UserLimitEntity limit(long uid, long gid, ResourceType type, double value) {
        UserLimitEntity l = new UserLimitEntity();
        l.setUserId(uid); l.setGroupId(gid); l.setResourceType(type);
        l.setLimitValue(value); l.setIsBlocked(false);
        return l;
    }
    GroupPackageBalanceEntity balance(long gid, long period) {
        GroupPackageBalanceEntity b = new GroupPackageBalanceEntity();
        b.setGroupId(gid); b.setPeriodId(period);
        b.setRemainingMinutes(100L); b.setRemainingTraffic(500.0);
        return b;
    }

    @Test void createGroupSucceedsForValidRequest() {
        setupCreateGroupStubs();
        when(userRepo.findByIdIn(anyList())).thenReturn(List.of(user(1L), user(2L), user(3L)));
        when(memberRepo.findByUserIdInAndStatus(anyList(), eq(MemberStatus.ACTIVE))).thenReturn(List.of());
        FamilyGroupEntity result = svc.createGroup(1L, List.of(2L, 3L));
        assertEquals(100L, result.getId());
        assertEquals(1L, result.getOwnerId());
        assertEquals(GroupStatus.ACTIVE, result.getStatus());
        ArgumentCaptor<List<GroupMemberEntity>> cap = ArgumentCaptor.forClass(List.class);
        verify(memberRepo).saveAll(cap.capture());
        assertEquals(3, cap.getValue().size());
        long owners = cap.getValue().stream().filter(m -> m.getRole() == MemberRole.OWNER).count();
        assertEquals(1, owners);
    }

    @Test void createGroupRejectsTooFewMembers() {
        BusinessRuleViolationException ex = assertThrows(BusinessRuleViolationException.class,
            () -> svc.createGroup(1L, List.of()));
        assertEquals("INVALID_MEMBER_COUNT", ex.getErrorCode());
        assertEquals(HttpStatus.BAD_REQUEST, ex.getHttpStatus());
    }

    @Test void createGroupRejectsTooManyMembers() {
        BusinessRuleViolationException ex = assertThrows(BusinessRuleViolationException.class,
            () -> svc.createGroup(1L, List.of(2L, 3L, 4L, 5L)));
        assertEquals("INVALID_MEMBER_COUNT", ex.getErrorCode());
    }

    @Test void createGroupRejectsOwnerInMemberList() {
        BusinessRuleViolationException ex = assertThrows(BusinessRuleViolationException.class,
            () -> svc.createGroup(1L, List.of(1L, 2L)));
        assertEquals("INVALID_REQUEST_OWNER_IN_MEMBERS", ex.getErrorCode());
    }

    @Test void createGroupRejectsDuplicateMemberIds() {
        BusinessRuleViolationException ex = assertThrows(BusinessRuleViolationException.class,
            () -> svc.createGroup(1L, List.of(2L, 2L)));
        assertEquals("DUPLICATE_MEMBERS_IDS", ex.getErrorCode());
        assertEquals(HttpStatus.BAD_REQUEST, ex.getHttpStatus());
    }

    @Test void createGroupRejectsWhenAnyUserAlreadyInGroup() {
        GroupMemberEntity existing = member(50L, 2L, MemberRole.MEMBER, MemberStatus.ACTIVE);
        when(memberRepo.findByUserIdInAndStatus(anyList(), eq(MemberStatus.ACTIVE))).thenReturn(List.of(existing));
        BusinessRuleViolationException ex = assertThrows(BusinessRuleViolationException.class,
            () -> svc.createGroup(1L, List.of(2L, 3L)));
        assertEquals("BR_01_USER_ALREADY_IN_GROUP", ex.getErrorCode());
        assertEquals(HttpStatus.CONFLICT, ex.getHttpStatus());
    }

    @Test void createGroupRejectsWhenUsersNotFound() {
        when(memberRepo.findByUserIdInAndStatus(anyList(), eq(MemberStatus.ACTIVE))).thenReturn(List.of());
        when(userRepo.findByIdIn(anyList())).thenReturn(List.of(user(1L)));
        BusinessRuleViolationException ex = assertThrows(BusinessRuleViolationException.class,
            () -> svc.createGroup(1L, List.of(2L, 3L)));
        assertEquals("USER_NOT_FOUND", ex.getErrorCode());
        assertEquals(HttpStatus.NOT_FOUND, ex.getHttpStatus());
    }

    @Test void createGroupWritesAuditLogsForEachMember() {
        setupCreateGroupStubs();
        when(userRepo.findByIdIn(anyList())).thenReturn(List.of(user(1L), user(2L)));
        when(memberRepo.findByUserIdInAndStatus(anyList(), eq(MemberStatus.ACTIVE))).thenReturn(List.of());
        svc.createGroup(1L, List.of(2L));
        ArgumentCaptor<List<AuditLogEntity>> cap = ArgumentCaptor.forClass(List.class);
        verify(auditRepo).saveAll(cap.capture());
        assertEquals(2, cap.getValue().size());
        assertTrue(cap.getValue().stream().allMatch(a -> a.getActionType() == ActionType.ADD_MEMBER));
    }

    @Test void addMemberAddsActiveMemberAndAuditLog() {
        setupSingleSaveStubs();
        FamilyGroupEntity g = group(10L, 1L, GroupStatus.ACTIVE);
        when(groupRepo.findByIdAndStatus(10L, GroupStatus.ACTIVE)).thenReturn(Optional.of(g));
        when(userRepo.findById(5L)).thenReturn(Optional.of(user(5L)));
        when(memberRepo.findByUserIdAndStatus(5L, MemberStatus.ACTIVE)).thenReturn(Optional.empty());
        when(memberRepo.countByGroupIdAndStatus(10L, MemberStatus.ACTIVE)).thenReturn(2L);

        svc.addMember(10L, 5L, 1L);

        ArgumentCaptor<GroupMemberEntity> cap = ArgumentCaptor.forClass(GroupMemberEntity.class);
        verify(memberRepo).save(cap.capture());
        assertEquals(MemberRole.MEMBER, cap.getValue().getRole());
        assertEquals(MemberStatus.ACTIVE, cap.getValue().getStatus());
        verify(auditRepo).save(any(AuditLogEntity.class));
    }

    @Test void addMemberRejectsWhenGroupNotFound() {
        when(groupRepo.findByIdAndStatus(99L, GroupStatus.ACTIVE)).thenReturn(Optional.empty());
        BusinessRuleViolationException ex = assertThrows(BusinessRuleViolationException.class,
            () -> svc.addMember(99L, 5L, 1L));
        assertEquals("GROUP_NOT_FOUND", ex.getErrorCode());
    }

    @Test void addMemberRejectsWhenUserNotFound() {
        FamilyGroupEntity g = group(10L, 1L, GroupStatus.ACTIVE);
        when(groupRepo.findByIdAndStatus(10L, GroupStatus.ACTIVE)).thenReturn(Optional.of(g));
        when(userRepo.findById(5L)).thenReturn(Optional.empty());
        BusinessRuleViolationException ex = assertThrows(BusinessRuleViolationException.class,
            () -> svc.addMember(10L, 5L, 1L));
        assertEquals("USER_NOT_FOUND", ex.getErrorCode());
    }

    @Test void addMemberRejectsWhenUserAlreadyInAnyGroup() {
        FamilyGroupEntity g = group(10L, 1L, GroupStatus.ACTIVE);
        when(groupRepo.findByIdAndStatus(10L, GroupStatus.ACTIVE)).thenReturn(Optional.of(g));
        when(userRepo.findById(5L)).thenReturn(Optional.of(user(5L)));
        when(memberRepo.findByUserIdAndStatus(5L, MemberStatus.ACTIVE)).thenReturn(Optional.of(member(50L, 5L, MemberRole.MEMBER, MemberStatus.ACTIVE)));
        BusinessRuleViolationException ex = assertThrows(BusinessRuleViolationException.class,
            () -> svc.addMember(10L, 5L, 1L));
        assertEquals("BR_01_USER_ALREADY_IN_GROUP", ex.getErrorCode());
        assertEquals(HttpStatus.CONFLICT, ex.getHttpStatus());
    }

    @Test void addMemberRejectsWhenGroupIsFull() {
        FamilyGroupEntity g = group(10L, 1L, GroupStatus.ACTIVE);
        when(groupRepo.findByIdAndStatus(10L, GroupStatus.ACTIVE)).thenReturn(Optional.of(g));
        when(userRepo.findById(5L)).thenReturn(Optional.of(user(5L)));
        when(memberRepo.findByUserIdAndStatus(5L, MemberStatus.ACTIVE)).thenReturn(Optional.empty());
        when(memberRepo.countByGroupIdAndStatus(10L, MemberStatus.ACTIVE)).thenReturn(4L);
        BusinessRuleViolationException ex = assertThrows(BusinessRuleViolationException.class,
            () -> svc.addMember(10L, 5L, 1L));
        assertEquals("BR_11_GROUP_IS_FULL", ex.getErrorCode());
    }

    @Test void removeMemberMarksTargetAsExcluded() {
        setupSingleSaveStubs();
        FamilyGroupEntity g = group(10L, 1L, GroupStatus.ACTIVE);
        GroupMemberEntity target = member(10L, 5L, MemberRole.MEMBER, MemberStatus.ACTIVE);
        when(groupRepo.findByIdAndStatus(10L, GroupStatus.ACTIVE)).thenReturn(Optional.of(g));
        when(memberRepo.findByGroupIdAndUserIdAndStatus(10L, 5L, MemberStatus.ACTIVE)).thenReturn(Optional.of(target));
        svc.removeMember(10L, 5L, 1L);
        assertEquals(MemberStatus.EXCLUDED, target.getStatus());
        verify(memberRepo).save(target);
        ArgumentCaptor<AuditLogEntity> cap = ArgumentCaptor.forClass(AuditLogEntity.class);
        verify(auditRepo).save(cap.capture());
        assertEquals(ActionType.REMOVE_MEMBER, cap.getValue().getActionType());
    }

    @Test void removeMemberInvalidatesLimits() {
        setupSingleSaveStubs();
        FamilyGroupEntity g = group(10L, 1L, GroupStatus.ACTIVE);
        GroupMemberEntity target = member(10L, 5L, MemberRole.MEMBER, MemberStatus.ACTIVE);
        UserLimitEntity traffic = limit(5L, 10L, ResourceType.TRAFFIC, 100.0);
        when(groupRepo.findByIdAndStatus(10L, GroupStatus.ACTIVE)).thenReturn(Optional.of(g));
        when(memberRepo.findByGroupIdAndUserIdAndStatus(10L, 5L, MemberStatus.ACTIVE)).thenReturn(Optional.of(target));
        when(limitRepo.findByUserIdAndGroupIdAndResourceType(5L, 10L, ResourceType.TRAFFIC)).thenReturn(Optional.of(traffic));

        svc.removeMember(10L, 5L, 1L);

        assertEquals(0.0, traffic.getLimitValue());
        assertTrue(traffic.getIsBlocked());
        verify(limitRepo).saveAll(List.of(traffic));
    }

    @Test void removeMemberRejectsWhenTryingToRemoveOwner() {
        FamilyGroupEntity g = group(10L, 1L, GroupStatus.ACTIVE);
        when(groupRepo.findByIdAndStatus(10L, GroupStatus.ACTIVE)).thenReturn(Optional.of(g));
        BusinessRuleViolationException ex = assertThrows(BusinessRuleViolationException.class,
            () -> svc.removeMember(10L, 1L, 1L));
        assertEquals("BR_22_REMOVE_OWNER", ex.getErrorCode());
    }

    @Test void removeMemberRejectsWhenTargetNotInGroup() {
        FamilyGroupEntity g = group(10L, 1L, GroupStatus.ACTIVE);
        when(groupRepo.findByIdAndStatus(10L, GroupStatus.ACTIVE)).thenReturn(Optional.of(g));
        when(memberRepo.findByGroupIdAndUserIdAndStatus(10L, 5L, MemberStatus.ACTIVE)).thenReturn(Optional.empty());
        BusinessRuleViolationException ex = assertThrows(BusinessRuleViolationException.class,
            () -> svc.removeMember(10L, 5L, 1L));
        assertEquals("USER_NOT_IN_TARGET_GROUP", ex.getErrorCode());
    }

    @Test void leaveGroupMarksUserAsLeft() {
        setupSingleSaveStubs();
        FamilyGroupEntity g = group(10L, 1L, GroupStatus.ACTIVE);
        GroupMemberEntity me = member(10L, 5L, MemberRole.MEMBER, MemberStatus.ACTIVE);
        when(groupRepo.findByIdAndStatus(10L, GroupStatus.ACTIVE)).thenReturn(Optional.of(g));
        when(memberRepo.findByGroupIdAndUserIdAndStatus(10L, 5L, MemberStatus.ACTIVE)).thenReturn(Optional.of(me));
        svc.leaveGroup(10L, 5L, 5L);
        assertEquals(MemberStatus.LEAVE, me.getStatus());
        verify(memberRepo).save(me);
        ArgumentCaptor<AuditLogEntity> cap = ArgumentCaptor.forClass(AuditLogEntity.class);
        verify(auditRepo).save(cap.capture());
        assertEquals(ActionType.MEMBER_LEFT, cap.getValue().getActionType());
    }

    @Test void leaveGroupInvalidatesLimits() {
        setupSingleSaveStubs();
        FamilyGroupEntity g = group(10L, 1L, GroupStatus.ACTIVE);
        GroupMemberEntity me = member(10L, 5L, MemberRole.MEMBER, MemberStatus.ACTIVE);
        UserLimitEntity minutes = limit(5L, 10L, ResourceType.MINUTES, 50.0);
        when(groupRepo.findByIdAndStatus(10L, GroupStatus.ACTIVE)).thenReturn(Optional.of(g));
        when(memberRepo.findByGroupIdAndUserIdAndStatus(10L, 5L, MemberStatus.ACTIVE)).thenReturn(Optional.of(me));
        when(limitRepo.findByUserIdAndGroupIdAndResourceType(5L, 10L, ResourceType.MINUTES)).thenReturn(Optional.of(minutes));

        svc.leaveGroup(10L, 5L, 5L);

        assertEquals(0.0, minutes.getLimitValue());
        assertTrue(minutes.getIsBlocked());
        verify(limitRepo).saveAll(List.of(minutes));
    }

    @Test void leaveGroupRejectsWhenOwnerTriesToLeave() {
        FamilyGroupEntity g = group(10L, 1L, GroupStatus.ACTIVE);
        when(groupRepo.findByIdAndStatus(10L, GroupStatus.ACTIVE)).thenReturn(Optional.of(g));
        BusinessRuleViolationException ex = assertThrows(BusinessRuleViolationException.class,
            () -> svc.leaveGroup(10L, 1L, 1L));
        assertEquals("BR_23_OWNER_LEAVE", ex.getErrorCode());
    }

    @Test void leaveGroupRejectsWhenUserNotInGroup() {
        FamilyGroupEntity g = group(10L, 1L, GroupStatus.ACTIVE);
        when(groupRepo.findByIdAndStatus(10L, GroupStatus.ACTIVE)).thenReturn(Optional.of(g));
        when(memberRepo.findByGroupIdAndUserIdAndStatus(10L, 5L, MemberStatus.ACTIVE)).thenReturn(Optional.empty());
        BusinessRuleViolationException ex = assertThrows(BusinessRuleViolationException.class,
            () -> svc.leaveGroup(10L, 5L, 5L));
        assertEquals("USER_NOT_IN_TARGET_GROUP", ex.getErrorCode());
    }

    @Test void transferOwnershipSwapsRolesAndUpdatesGroup() {
        setupTransferStubs();
        FamilyGroupEntity g = group(10L, 1L, GroupStatus.ACTIVE);
        GroupMemberEntity oldOwner = member(10L, 1L, MemberRole.OWNER, MemberStatus.ACTIVE);
        GroupMemberEntity newOwner = member(10L, 2L, MemberRole.MEMBER, MemberStatus.ACTIVE);
        when(groupRepo.findByIdAndStatus(10L, GroupStatus.ACTIVE)).thenReturn(Optional.of(g));
        when(memberRepo.findByGroupIdAndUserIdAndStatus(10L, 1L, MemberStatus.ACTIVE)).thenReturn(Optional.of(oldOwner));
        when(memberRepo.findByGroupIdAndUserIdAndStatus(10L, 2L, MemberStatus.ACTIVE)).thenReturn(Optional.of(newOwner));
        svc.transferOwnership(10L, 2L, 1L);
        assertEquals(MemberRole.MEMBER, oldOwner.getRole());
        assertEquals(MemberRole.OWNER, newOwner.getRole());
        assertEquals(2L, g.getOwnerId());
        verify(groupRepo).save(g);
        ArgumentCaptor<AuditLogEntity> cap = ArgumentCaptor.forClass(AuditLogEntity.class);
        verify(auditRepo).save(cap.capture());
        assertEquals(ActionType.OWNERSHIP_TRANSFERRED, cap.getValue().getActionType());
    }

    @Test void transferOwnershipRejectsSameOwner() {
        FamilyGroupEntity g = group(10L, 1L, GroupStatus.ACTIVE);
        when(groupRepo.findByIdAndStatus(10L, GroupStatus.ACTIVE)).thenReturn(Optional.of(g));
        BusinessRuleViolationException ex = assertThrows(BusinessRuleViolationException.class,
            () -> svc.transferOwnership(10L, 1L, 1L));
        assertEquals("BR_25_SAME_OWNER", ex.getErrorCode());
    }

    @Test void transferOwnershipRejectsWhenNewOwnerNotMember() {
        FamilyGroupEntity g = group(10L, 1L, GroupStatus.ACTIVE);
        GroupMemberEntity oldOwner = member(10L, 1L, MemberRole.OWNER, MemberStatus.ACTIVE);
        when(groupRepo.findByIdAndStatus(10L, GroupStatus.ACTIVE)).thenReturn(Optional.of(g));
        when(memberRepo.findByGroupIdAndUserIdAndStatus(10L, 1L, MemberStatus.ACTIVE)).thenReturn(Optional.of(oldOwner));
        when(memberRepo.findByGroupIdAndUserIdAndStatus(10L, 2L, MemberStatus.ACTIVE)).thenReturn(Optional.empty());
        BusinessRuleViolationException ex = assertThrows(BusinessRuleViolationException.class,
            () -> svc.transferOwnership(10L, 2L, 1L));
        assertEquals("BR_24_NEW_OWNER_NOT_MEMBER", ex.getErrorCode());
        assertEquals(HttpStatus.NOT_FOUND, ex.getHttpStatus());
    }

    @Test void disbandGroupSetsDisbandedStatusAndExcludesMembers() {
        setupDisbandStubs();
        setupNoLimitsOrBalance();
        FamilyGroupEntity g = group(10L, 1L, GroupStatus.ACTIVE);
        GroupMemberEntity m1 = member(10L, 1L, MemberRole.OWNER, MemberStatus.ACTIVE);
        GroupMemberEntity m2 = member(10L, 2L, MemberRole.MEMBER, MemberStatus.ACTIVE);
        when(groupRepo.findByIdAndStatus(10L, GroupStatus.ACTIVE)).thenReturn(Optional.of(g));
        when(memberRepo.findByGroupIdAndStatus(10L, MemberStatus.ACTIVE)).thenReturn(List.of(m1, m2));
        svc.disbandGroup(10L, 1L);
        assertEquals(GroupStatus.DISBANDED, g.getStatus());
        assertEquals(MemberStatus.EXCLUDED, m1.getStatus());
        assertEquals(MemberStatus.EXCLUDED, m2.getStatus());
        verify(groupRepo).save(g);
        verify(memberRepo).saveAll(List.of(m1, m2));
        ArgumentCaptor<AuditLogEntity> cap = ArgumentCaptor.forClass(AuditLogEntity.class);
        verify(auditRepo).save(cap.capture());
        assertEquals(ActionType.DISBAND_GROUP, cap.getValue().getActionType());
    }

    @Test void disbandGroupInvalidatesLimitsAndZerosBalance() {
        setupDisbandStubs();
        FamilyGroupEntity g = group(10L, 1L, GroupStatus.ACTIVE);
        GroupMemberEntity m1 = member(10L, 1L, MemberRole.OWNER, MemberStatus.ACTIVE);
        GroupMemberEntity m2 = member(10L, 2L, MemberRole.MEMBER, MemberStatus.ACTIVE);
        UserLimitEntity traffic = limit(2L, 10L, ResourceType.TRAFFIC, 100.0);
        GroupPackageBalanceEntity b = balance(10L, 1L);
        when(groupRepo.findByIdAndStatus(10L, GroupStatus.ACTIVE)).thenReturn(Optional.of(g));
        when(memberRepo.findByGroupIdAndStatus(10L, MemberStatus.ACTIVE)).thenReturn(List.of(m1, m2));
        when(limitRepo.findByUserIdAndGroupIdAndResourceType(eq(1L), eq(10L), any(ResourceType.class)))
            .thenReturn(Optional.empty());
        when(limitRepo.findByUserIdAndGroupIdAndResourceType(2L, 10L, ResourceType.TRAFFIC))
            .thenReturn(Optional.of(traffic));
        when(balanceRepo.findByGroupIdAndPeriodIdForUpdate(10L, 1L)).thenReturn(Optional.of(b));

        svc.disbandGroup(10L, 1L);

        assertEquals(0.0, traffic.getLimitValue());
        assertTrue(traffic.getIsBlocked());
        assertEquals(0L, b.getRemainingMinutes());
        assertEquals(0.0, b.getRemainingTraffic());
        verify(balanceRepo).save(b);
        verify(limitRepo, times(2)).saveAll(anyList());
        verify(limitRepo).saveAll(List.of(traffic));
    }

    @Test void disbandGroupRejectsWhenGroupNotFound() {
        when(groupRepo.findByIdAndStatus(99L, GroupStatus.ACTIVE)).thenReturn(Optional.empty());
        BusinessRuleViolationException ex = assertThrows(BusinessRuleViolationException.class,
            () -> svc.disbandGroup(99L, 1L));
        assertEquals("GROUP_NOT_FOUND", ex.getErrorCode());
    }

    @Test void disbandGroupDoesNotDoubleExcludeAlreadyExcludedMembers() {
        setupDisbandStubs();
        setupNoLimitsOrBalance();
        FamilyGroupEntity g = group(10L, 1L, GroupStatus.ACTIVE);
        GroupMemberEntity active = member(10L, 1L, MemberRole.OWNER, MemberStatus.ACTIVE);
        GroupMemberEntity alreadyExcluded = member(10L, 2L, MemberRole.MEMBER, MemberStatus.EXCLUDED);
        when(groupRepo.findByIdAndStatus(10L, GroupStatus.ACTIVE)).thenReturn(Optional.of(g));
        when(memberRepo.findByGroupIdAndStatus(10L, MemberStatus.ACTIVE)).thenReturn(List.of(active));
        svc.disbandGroup(10L, 1L);
        verify(memberRepo).saveAll(List.of(active));
        assertEquals(MemberStatus.EXCLUDED, alreadyExcluded.getStatus());
    }
}
