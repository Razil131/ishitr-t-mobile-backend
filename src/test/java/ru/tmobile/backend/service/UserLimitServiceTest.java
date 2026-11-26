package ru.tmobile.backend.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import ru.tmobile.backend.dto.familyGroup.UserLimitResponse;
import ru.tmobile.backend.entity.AuditLogEntity;
import ru.tmobile.backend.entity.AuditLogEntity.ActionType;
import ru.tmobile.backend.entity.FamilyGroupEntity;
import ru.tmobile.backend.entity.FamilyGroupEntity.GroupStatus;
import ru.tmobile.backend.entity.GroupMemberEntity;
import ru.tmobile.backend.entity.GroupMemberEntity.MemberStatus;
import ru.tmobile.backend.entity.GroupSubscriptionEntity;
import ru.tmobile.backend.entity.ServicePackageEntity;
import ru.tmobile.backend.entity.UserLimitEntity;
import ru.tmobile.backend.entity.UserUsageEntity;
import ru.tmobile.backend.entity.enums.ResourceType;
import ru.tmobile.backend.exception.BusinessRuleViolationException;
import ru.tmobile.backend.repository.FamilyGroupRepository;
import ru.tmobile.backend.repository.GroupMemberRepository;
import ru.tmobile.backend.repository.GroupSubscriptionRepository;
import ru.tmobile.backend.repository.ServicePackageRepository;
import ru.tmobile.backend.repository.UserLimitRepository;
import ru.tmobile.backend.repository.UserUsageRepository;
import ru.tmobile.backend.virtual_clock.VirtualClockService;

@ExtendWith(MockitoExtension.class)
class UserLimitServiceTest {
    @Mock UserLimitRepository limitRepo;
    @Mock UserUsageRepository usageRepo;
    @Mock GroupSubscriptionRepository subscriptionRepo;
    @Mock ServicePackageRepository packageRepo;
    @Mock FamilyGroupRepository groupRepo;
    @Mock GroupMemberRepository memberRepo;
    @Mock VirtualClockService clock;
    @Mock GroupAuditService auditService;

    UserLimitService svc;
    OffsetDateTime now;
    Instant nowInstant;

    @BeforeEach
    void setUp() {
        svc = new UserLimitService(limitRepo, usageRepo, subscriptionRepo, packageRepo,
            groupRepo, memberRepo, clock, auditService);
        now = OffsetDateTime.of(2026, 1, 15, 12, 0, 0, 0, ZoneOffset.UTC);
        nowInstant = now.toInstant();
    }

    private FamilyGroupEntity activeGroup(Long groupId, Long ownerId) {
        FamilyGroupEntity g = new FamilyGroupEntity();
        g.setId(groupId);
        g.setOwnerId(ownerId);
        g.setStatus(GroupStatus.ACTIVE);
        g.setCreatedAt(now);
        return g;
    }

    private GroupMemberEntity activeMember(Long groupId, Long userId) {
        GroupMemberEntity m = new GroupMemberEntity();
        m.setGroupId(groupId);
        m.setUserId(userId);
        m.setStatus(MemberStatus.ACTIVE);
        m.setJoinedAt(now);
        return m;
    }

    private ServicePackageEntity pkg(Long id, Double traffic, Long minutes) {
        ServicePackageEntity p = new ServicePackageEntity();
        p.setId(id);
        p.setName("Test Package");
        p.setTotalTraffic(traffic);
        p.setTotalMinutes(minutes);
        return p;
    }

    private GroupSubscriptionEntity subscription(Long groupId, Long packageId) {
        GroupSubscriptionEntity s = new GroupSubscriptionEntity();
        s.setGroupId(groupId);
        s.setPackageId(packageId);
        s.setPeriodStart(now);
        s.setPeriodEnd(now.plusMonths(1));
        return s;
    }

    private void setupHappyPath(Long groupId, Long userId, Long ownerId) {
        FamilyGroupEntity group = activeGroup(groupId, ownerId);
        GroupMemberEntity member = activeMember(groupId, userId);
        ServicePackageEntity pkg = pkg(100L, 1000.0, 500L);
        GroupSubscriptionEntity sub = subscription(groupId, 100L);

        when(clock.instant()).thenReturn(nowInstant);
        when(groupRepo.findById(groupId)).thenReturn(Optional.of(group));
        when(memberRepo.findByGroupIdAndUserIdAndStatus(groupId, userId, MemberStatus.ACTIVE))
            .thenReturn(Optional.of(member));
        when(subscriptionRepo.findActiveByGroupId(eq(groupId), any())).thenReturn(Optional.of(sub));
        when(packageRepo.findById(100L)).thenReturn(Optional.of(pkg));
        when(usageRepo.findByUserIdAndGroupIdAndPeriodIdAndResourceType(eq(userId), eq(groupId), eq(1L), any(ResourceType.class)))
            .thenReturn(Optional.empty());
        when(limitRepo.findByUserIdAndGroupIdAndResourceType(eq(userId), eq(groupId), any(ResourceType.class)))
            .thenReturn(Optional.empty());
        when(limitRepo.save(any())).thenAnswer(i -> i.getArgument(0));
        when(auditService.log(any(), any(), any(), any(), any(), any()))
            .thenReturn(new AuditLogEntity());
    }

    @Test
    void setMemberLimitCreatesNewLimitWhenNotExists() {
        Long groupId = 1L, userId = 2L, callerId = 1L;
        setupHappyPath(groupId, userId, callerId);

        UserLimitResponse result = svc.setMemberLimit(groupId, userId, "TRAFFIC", 100.0, callerId);

        assertEquals(userId, result.userId());
        assertEquals(groupId, result.groupId());
        assertEquals("TRAFFIC", result.resourceType());
        assertEquals(100.0, result.limitValue());
        assertEquals(0.0, result.usedValue());
        assertEquals("ACTIVE", result.status());
        assertEquals(50.0, result.minAllowed());
        assertEquals(1000.0, result.maxAllowed());

        ArgumentCaptor<UserLimitEntity> limitCaptor = ArgumentCaptor.forClass(UserLimitEntity.class);
        verify(limitRepo).save(limitCaptor.capture());
        UserLimitEntity saved = limitCaptor.getValue();
        assertEquals(userId, saved.getUserId());
        assertEquals(groupId, saved.getGroupId());
        assertEquals(ResourceType.TRAFFIC, saved.getResourceType());
        assertEquals(100.0, saved.getLimitValue());
        assertFalse(saved.getIsBlocked());
    }

    @Test
    void setMemberLimitUpdatesExistingLimit() {
        Long groupId = 1L, userId = 2L, callerId = 1L;
        FamilyGroupEntity group = activeGroup(groupId, callerId);
        GroupMemberEntity member = activeMember(groupId, userId);
        ServicePackageEntity pkg = pkg(100L, 1000.0, 500L);
        GroupSubscriptionEntity sub = subscription(groupId, 100L);

        UserLimitEntity existing = new UserLimitEntity();
        existing.setUserId(userId);
        existing.setGroupId(groupId);
        existing.setResourceType(ResourceType.MINUTES);
        existing.setLimitValue(50.0);
        existing.setIsBlocked(false);

        when(clock.instant()).thenReturn(nowInstant);
        when(groupRepo.findById(groupId)).thenReturn(Optional.of(group));
        when(memberRepo.findByGroupIdAndUserIdAndStatus(groupId, userId, MemberStatus.ACTIVE))
            .thenReturn(Optional.of(member));
        when(subscriptionRepo.findActiveByGroupId(eq(groupId), any())).thenReturn(Optional.of(sub));
        when(packageRepo.findById(100L)).thenReturn(Optional.of(pkg));
        when(usageRepo.findByUserIdAndGroupIdAndPeriodIdAndResourceType(userId, groupId, 1L, ResourceType.MINUTES))
            .thenReturn(Optional.empty());
        when(limitRepo.findByUserIdAndGroupIdAndResourceType(userId, groupId, ResourceType.MINUTES))
            .thenReturn(Optional.of(existing));
        when(limitRepo.save(any())).thenAnswer(i -> i.getArgument(0));
        when(auditService.log(any(), any(), any(), any(), any(), any()))
            .thenReturn(new AuditLogEntity());

        UserLimitResponse result = svc.setMemberLimit(groupId, userId, "MINUTES", 200.0, callerId);

        assertEquals(200.0, result.limitValue());
        assertEquals("MINUTES", result.resourceType());
        verify(limitRepo).save(existing);
        assertEquals(200.0, existing.getLimitValue());
    }

    @Test
    void setMemberLimitReturnsBlockedWhenLimitLessThanUsed() {
        Long groupId = 1L, userId = 2L, callerId = 1L;
        FamilyGroupEntity group = activeGroup(groupId, callerId);
        GroupMemberEntity member = activeMember(groupId, userId);
        ServicePackageEntity pkg = pkg(100L, 1000.0, 500L);
        GroupSubscriptionEntity sub = subscription(groupId, 100L);

        UserUsageEntity usage = new UserUsageEntity();
        usage.setUsedValue(150.0);

        when(clock.instant()).thenReturn(nowInstant);
        when(groupRepo.findById(groupId)).thenReturn(Optional.of(group));
        when(memberRepo.findByGroupIdAndUserIdAndStatus(groupId, userId, MemberStatus.ACTIVE))
            .thenReturn(Optional.of(member));
        when(subscriptionRepo.findActiveByGroupId(eq(groupId), any())).thenReturn(Optional.of(sub));
        when(packageRepo.findById(100L)).thenReturn(Optional.of(pkg));
        when(usageRepo.findByUserIdAndGroupIdAndPeriodIdAndResourceType(userId, groupId, 1L, ResourceType.TRAFFIC))
            .thenReturn(Optional.of(usage));
        when(limitRepo.findByUserIdAndGroupIdAndResourceType(userId, groupId, ResourceType.TRAFFIC))
            .thenReturn(Optional.empty());
        when(limitRepo.save(any())).thenAnswer(i -> i.getArgument(0));
        when(auditService.log(any(), any(), any(), any(), any(), any()))
            .thenReturn(new AuditLogEntity());

        UserLimitResponse result = svc.setMemberLimit(groupId, userId, "TRAFFIC", 100.0, callerId);

        assertEquals("BLOCKED", result.status());
        ArgumentCaptor<UserLimitEntity> captor = ArgumentCaptor.forClass(UserLimitEntity.class);
        verify(limitRepo).save(captor.capture());
        assertTrue(captor.getValue().getIsBlocked());
    }

    @Test
    void setMemberLimitReturnsNotSetWhenLimitEqualsMaxAllowed() {
        Long groupId = 1L, userId = 2L, callerId = 1L;
        setupHappyPath(groupId, userId, callerId);

        UserLimitResponse result = svc.setMemberLimit(groupId, userId, "TRAFFIC", 1000.0, callerId);

        assertEquals("NOT_SET", result.status());
    }

    @Test
    void setMemberLimitAcceptsBoundaryAt5Percent() {
        Long groupId = 1L, userId = 2L, callerId = 1L;
        setupHappyPath(groupId, userId, callerId);

        UserLimitResponse result = svc.setMemberLimit(groupId, userId, "TRAFFIC", 50.0, callerId);

        assertEquals(50.0, result.limitValue());
        assertEquals("ACTIVE", result.status());
    }

    @Test
    void setMemberLimitAcceptsBoundaryAt100Percent() {
        Long groupId = 1L, userId = 2L, callerId = 1L;
        setupHappyPath(groupId, userId, callerId);

        UserLimitResponse result = svc.setMemberLimit(groupId, userId, "TRAFFIC", 1000.0, callerId);

        assertEquals(1000.0, result.limitValue());
        assertEquals("NOT_SET", result.status());
    }

    @Test
    void setMemberLimitRejectsWhenGroupNotFound() {
        when(groupRepo.findById(99L)).thenReturn(Optional.empty());

        BusinessRuleViolationException ex = assertThrows(BusinessRuleViolationException.class,
            () -> svc.setMemberLimit(99L, 2L, "TRAFFIC", 100.0, 1L));
        assertEquals("GROUP_NOT_FOUND", ex.getErrorCode());
        assertEquals(HttpStatus.NOT_FOUND, ex.getHttpStatus());
    }

    @Test
    void setMemberLimitRejectsWhenGroupDisbanded() {
        FamilyGroupEntity group = activeGroup(1L, 1L);
        group.setStatus(GroupStatus.DISBANDED);
        when(groupRepo.findById(1L)).thenReturn(Optional.of(group));

        BusinessRuleViolationException ex = assertThrows(BusinessRuleViolationException.class,
            () -> svc.setMemberLimit(1L, 2L, "TRAFFIC", 100.0, 1L));
        assertEquals("GROUP_DISBANDED", ex.getErrorCode());
        assertEquals(HttpStatus.CONFLICT, ex.getHttpStatus());
    }

    @Test
    void setMemberLimitRejectsWhenCallerNotOwner() {
        FamilyGroupEntity group = activeGroup(1L, 1L);
        when(groupRepo.findById(1L)).thenReturn(Optional.of(group));

        BusinessRuleViolationException ex = assertThrows(BusinessRuleViolationException.class,
            () -> svc.setMemberLimit(1L, 2L, "TRAFFIC", 100.0, 99L));
        assertEquals("FORBIDDEN", ex.getErrorCode());
        assertEquals(HttpStatus.FORBIDDEN, ex.getHttpStatus());
    }

    @Test
    void setMemberLimitRejectsInvalidResourceType() {
        FamilyGroupEntity group = activeGroup(1L, 1L);
        when(groupRepo.findById(1L)).thenReturn(Optional.of(group));

        BusinessRuleViolationException ex = assertThrows(BusinessRuleViolationException.class,
            () -> svc.setMemberLimit(1L, 2L, "INVALID", 100.0, 1L));
        assertEquals("INVALID_RESOURCE_TYPE", ex.getErrorCode());
        assertEquals(HttpStatus.BAD_REQUEST, ex.getHttpStatus());
    }

    @Test
    void setMemberLimitRejectsWhenMemberNotActive() {
        FamilyGroupEntity group = activeGroup(1L, 1L);
        when(groupRepo.findById(1L)).thenReturn(Optional.of(group));
        when(memberRepo.findByGroupIdAndUserIdAndStatus(1L, 2L, MemberStatus.ACTIVE))
            .thenReturn(Optional.empty());

        BusinessRuleViolationException ex = assertThrows(BusinessRuleViolationException.class,
            () -> svc.setMemberLimit(1L, 2L, "TRAFFIC", 100.0, 1L));
        assertEquals("MEMBER_NOT_ACTIVE", ex.getErrorCode());
        assertEquals(HttpStatus.CONFLICT, ex.getHttpStatus());
    }

    @Test
    void setMemberLimitRejectsWhenLimitBelowMinAllowed() {
        Long groupId = 1L, userId = 2L, callerId = 1L;
        FamilyGroupEntity group = activeGroup(groupId, callerId);
        GroupMemberEntity member = activeMember(groupId, userId);
        ServicePackageEntity pkg = pkg(100L, 1000.0, 500L);
        GroupSubscriptionEntity sub = subscription(groupId, 100L);

        when(clock.instant()).thenReturn(nowInstant);
        when(groupRepo.findById(groupId)).thenReturn(Optional.of(group));
        when(memberRepo.findByGroupIdAndUserIdAndStatus(groupId, userId, MemberStatus.ACTIVE))
            .thenReturn(Optional.of(member));
        when(subscriptionRepo.findActiveByGroupId(eq(groupId), any())).thenReturn(Optional.of(sub));
        when(packageRepo.findById(100L)).thenReturn(Optional.of(pkg));

        BusinessRuleViolationException ex = assertThrows(BusinessRuleViolationException.class,
            () -> svc.setMemberLimit(groupId, userId, "TRAFFIC", 49.0, callerId));
        assertEquals("INVALID_LIMIT_VALUE", ex.getErrorCode());
        assertEquals(HttpStatus.BAD_REQUEST, ex.getHttpStatus());
    }

    @Test
    void setMemberLimitRejectsWhenLimitAboveMaxAllowed() {
        Long groupId = 1L, userId = 2L, callerId = 1L;
        FamilyGroupEntity group = activeGroup(groupId, callerId);
        GroupMemberEntity member = activeMember(groupId, userId);
        ServicePackageEntity pkg = pkg(100L, 1000.0, 500L);
        GroupSubscriptionEntity sub = subscription(groupId, 100L);

        when(clock.instant()).thenReturn(nowInstant);
        when(groupRepo.findById(groupId)).thenReturn(Optional.of(group));
        when(memberRepo.findByGroupIdAndUserIdAndStatus(groupId, userId, MemberStatus.ACTIVE))
            .thenReturn(Optional.of(member));
        when(subscriptionRepo.findActiveByGroupId(eq(groupId), any())).thenReturn(Optional.of(sub));
        when(packageRepo.findById(100L)).thenReturn(Optional.of(pkg));

        BusinessRuleViolationException ex = assertThrows(BusinessRuleViolationException.class,
            () -> svc.setMemberLimit(groupId, userId, "TRAFFIC", 1001.0, callerId));
        assertEquals("INVALID_LIMIT_VALUE", ex.getErrorCode());
    }

    @Test
    void setMemberLimitWritesAuditOnceWithCorrectDetails() {
        Long groupId = 1L, userId = 2L, callerId = 1L;
        setupHappyPath(groupId, userId, callerId);

        svc.setMemberLimit(groupId, userId, "TRAFFIC", 100.0, callerId);

        ArgumentCaptor<ActionType> actionCap = ArgumentCaptor.forClass(ActionType.class);
        ArgumentCaptor<Long> actorCap = ArgumentCaptor.forClass(Long.class);
        ArgumentCaptor<Long> targetCap = ArgumentCaptor.forClass(Long.class);
        verify(auditService).log(eq(groupId), actorCap.capture(), targetCap.capture(),
            actionCap.capture(), any(), any());
        assertEquals(ActionType.LIMIT_CHANGED, actionCap.getValue());
        assertEquals(callerId, actorCap.getValue());
        assertEquals(userId, targetCap.getValue());

        verify(auditService).save(any(AuditLogEntity.class));
        verify(auditService, times(1)).save(any());
    }

    @Test
    void setMemberLimitUsesMinutesTotalWhenResourceTypeMinutes() {
        Long groupId = 1L, userId = 2L, callerId = 1L;
        setupHappyPath(groupId, userId, callerId);

        UserLimitResponse result = svc.setMemberLimit(groupId, userId, "MINUTES", 500.0, callerId);

        assertEquals(500.0, result.maxAllowed());
        assertEquals(25.0, result.minAllowed());
        assertEquals("NOT_SET", result.status());
    }

    @Test
    void invalidateMemberLimitsBlocksAllTypes() {
        Long groupId = 10L, userId = 2L;
        UserLimitEntity traffic = new UserLimitEntity();
        traffic.setUserId(userId); traffic.setGroupId(groupId);
        traffic.setResourceType(ResourceType.TRAFFIC);
        traffic.setLimitValue(100.0); traffic.setIsBlocked(false);

        UserLimitEntity minutes = new UserLimitEntity();
        minutes.setUserId(userId); minutes.setGroupId(groupId);
        minutes.setResourceType(ResourceType.MINUTES);
        minutes.setLimitValue(50.0); minutes.setIsBlocked(false);

        when(limitRepo.findByUserIdAndGroupIdAndResourceType(userId, groupId, ResourceType.TRAFFIC))
            .thenReturn(Optional.of(traffic));
        when(limitRepo.findByUserIdAndGroupIdAndResourceType(userId, groupId, ResourceType.MINUTES))
            .thenReturn(Optional.of(minutes));

        svc.invalidateMemberLimits(groupId, userId);

        assertEquals(0.0, traffic.getLimitValue());
        assertTrue(traffic.getIsBlocked());
        assertEquals(0.0, minutes.getLimitValue());
        assertTrue(minutes.getIsBlocked());
        ArgumentCaptor<Iterable<UserLimitEntity>> cap = ArgumentCaptor.forClass(Iterable.class);
        verify(limitRepo).saveAll(cap.capture());
        assertEquals(2, (int) java.util.stream.StreamSupport.stream(cap.getValue().spliterator(), false).count());
    }

    @Test
    void invalidateMemberLimitsSkipsWhenNoLimits() {
        Long groupId = 10L, userId = 2L;
        when(limitRepo.findByUserIdAndGroupIdAndResourceType(eq(userId), eq(groupId), any(ResourceType.class)))
            .thenReturn(Optional.empty());

        svc.invalidateMemberLimits(groupId, userId);

        verify(limitRepo, never()).saveAll(any());
    }
}
