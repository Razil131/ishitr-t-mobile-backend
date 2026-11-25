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

import ru.tmobile.backend.entity.*;
import ru.tmobile.backend.entity.enums.ResourceType;
import ru.tmobile.backend.repository.*;
import ru.tmobile.backend.virtual_clock.VirtualClockService;

@ExtendWith(MockitoExtension.class)
class ConsumptionEventServiceTest {
    @Mock UserUsageRepository usageRepo;
    @Mock ConsumptionEventRepository eventRepo;
    @Mock UserLimitRepository limitRepo;
    @Mock GroupPackageBalanceRepository balanceRepo;
    @Mock GroupSubscriptionRepository subscriptionRepo;
    @Mock VirtualClockService clock;
    ConsumptionEventService svc;
    OffsetDateTime now;

    @BeforeEach void up() {
        svc = new ConsumptionEventService(usageRepo, eventRepo, limitRepo, balanceRepo, subscriptionRepo, clock);
        now = OffsetDateTime.of(2026, 1, 15, 12, 0, 0, 0, ZoneOffset.UTC);
    }

    void setupCommonStubs() {
        when(eventRepo.existsByEventId(any())).thenReturn(false);
        when(clock.instant()).thenReturn(now.toInstant());
    }

    UserLimitEntity limit(long uid, long gid, ResourceType t, double v, boolean blocked) {
        UserLimitEntity e = new UserLimitEntity();
        e.setUserId(uid); e.setGroupId(gid); e.setResourceType(t); e.setLimitValue(v); e.setIsBlocked(blocked);
        return e;
    }
    GroupPackageBalanceEntity balance(long gid, long pid, double traffic, long minutes) {
        GroupPackageBalanceEntity b = new GroupPackageBalanceEntity();
        b.setGroupId(gid); b.setPeriodId(pid); b.setRemainingTraffic(traffic); b.setRemainingMinutes(minutes);
        return b;
    }
    GroupSubscriptionEntity sub(long gid) {
        GroupSubscriptionEntity s = new GroupSubscriptionEntity();
        s.setGroupId(gid);
        s.setPeriodStart(now.minusDays(1));
        s.setPeriodEnd(now.plusDays(30));
        return s;
    }

    @Test void ignoresDuplicateEventId() {
        when(eventRepo.existsByEventId("dup")).thenReturn(true);
        svc.saveConsumptionEvent("dup", 1L, "TRAFFIC", 10.0, now, 5L);
        verify(eventRepo, never()).save(any());
    }

    @Test void ignoresNullAmount() {
        when(eventRepo.existsByEventId(any())).thenReturn(false);
        svc.saveConsumptionEvent("e", 1L, "TRAFFIC", null, now, 5L);
        verify(eventRepo, never()).save(any());
    }

    @Test void ignoresZeroAmount() {
        when(eventRepo.existsByEventId(any())).thenReturn(false);
        svc.saveConsumptionEvent("e", 1L, "TRAFFIC", 0.0, now, 5L);
        verify(eventRepo, never()).save(any());
    }

    @Test void ignoresNegativeAmount() {
        when(eventRepo.existsByEventId(any())).thenReturn(false);
        svc.saveConsumptionEvent("e", 1L, "TRAFFIC", -5.0, now, 5L);
        verify(eventRepo, never()).save(any());
    }

    @Test void usesCurrentTimeWhenTimestampMissing() {
        setupCommonStubs();
        ArgumentCaptor<ConsumptionEventEntity> cap = ArgumentCaptor.forClass(ConsumptionEventEntity.class);
        svc.saveConsumptionEvent("e", 1L, "TRAFFIC", 10.0, null, null);
        verify(eventRepo).save(cap.capture());
        assertNotNull(cap.getValue().getEventTimestamp());
    }

    @Test void throwsWhenResourceTypeInvalid() {
        setupCommonStubs();
        assertThrows(IllegalArgumentException.class,
            () -> svc.saveConsumptionEvent("e", 1L, "INVALID_TYPE", 10.0, now, null));
    }

    @Test void savesEventWithoutDeductionWhenGroupIdNull() {
        setupCommonStubs();
        svc.saveConsumptionEvent("e", 1L, "TRAFFIC", 10.0, now, null);
        verify(eventRepo).save(any());
        verify(balanceRepo, never()).findByGroupIdAndPeriodIdForUpdate(anyLong(), anyLong());
    }

    @Test void savesEventButSkipsDeductionWhenNoActiveSubscription() {
        setupCommonStubs();
        when(limitRepo.findByUserIdAndGroupIdAndResourceType(1L, 5L, ResourceType.TRAFFIC))
            .thenReturn(Optional.of(limit(1L, 5L, ResourceType.TRAFFIC, 100.0, false)));
        when(subscriptionRepo.findActiveByGroupId(eq(5L), any())).thenReturn(Optional.empty());
        svc.saveConsumptionEvent("e", 1L, "TRAFFIC", 10.0, now, 5L);
        verify(eventRepo).save(any());
        verify(balanceRepo, never()).findByGroupIdAndPeriodIdForUpdate(anyLong(), anyLong());
    }

    @Test void savesButSkipsDeductionWhenUserAlreadyBlocked() {
        setupCommonStubs();
        when(limitRepo.findByUserIdAndGroupIdAndResourceType(1L, 5L, ResourceType.TRAFFIC))
            .thenReturn(Optional.of(limit(1L, 5L, ResourceType.TRAFFIC, 100.0, true)));
        when(subscriptionRepo.findActiveByGroupId(eq(5L), any())).thenReturn(Optional.of(sub(5L)));
        svc.saveConsumptionEvent("e", 1L, "TRAFFIC", 10.0, now, 5L);
        verify(eventRepo).save(any());
        verify(balanceRepo, never()).findByGroupIdAndPeriodIdForUpdate(anyLong(), anyLong());
    }

    @Test void deductsTrafficFullyFromGroupBalance() {
        setupCommonStubs();
        UserLimitEntity ul = limit(1L, 5L, ResourceType.TRAFFIC, 100.0, false);
        GroupPackageBalanceEntity bal = balance(5L, 1L, 500.0, 1000L);
        when(limitRepo.findByUserIdAndGroupIdAndResourceType(1L, 5L, ResourceType.TRAFFIC)).thenReturn(Optional.of(ul));
        when(subscriptionRepo.findActiveByGroupId(eq(5L), any())).thenReturn(Optional.of(sub(5L)));
        when(balanceRepo.findByGroupIdAndPeriodIdForUpdate(5L, 1L)).thenReturn(Optional.of(bal));
        when(usageRepo.findByUserIdAndGroupIdAndPeriodIdAndResourceType(1L, 5L, 1L, ResourceType.TRAFFIC))
            .thenReturn(Optional.empty());

        svc.saveConsumptionEvent("e", 1L, "TRAFFIC", 50.0, now, 5L);

        assertEquals(450.0, bal.getRemainingTraffic());
        ArgumentCaptor<UserUsageEntity> uc = ArgumentCaptor.forClass(UserUsageEntity.class);
        verify(usageRepo).save(uc.capture());
        assertEquals(50.0, uc.getValue().getUsedValue());
        assertFalse(ul.getIsBlocked());
    }

    @Test void capsDeductionAtPersonalLimitRemaining() {
        setupCommonStubs();
        UserLimitEntity ul = limit(1L, 5L, ResourceType.TRAFFIC, 100.0, false);
        GroupPackageBalanceEntity bal = balance(5L, 1L, 500.0, 1000L);
        UserUsageEntity usage = new UserUsageEntity();
        usage.setUserId(1L); usage.setGroupId(5L); usage.setResourceType(ResourceType.TRAFFIC);
        usage.setUsedValue(90.0); usage.setPeriodId(1L);
        when(limitRepo.findByUserIdAndGroupIdAndResourceType(1L, 5L, ResourceType.TRAFFIC)).thenReturn(Optional.of(ul));
        when(subscriptionRepo.findActiveByGroupId(eq(5L), any())).thenReturn(Optional.of(sub(5L)));
        when(balanceRepo.findByGroupIdAndPeriodIdForUpdate(5L, 1L)).thenReturn(Optional.of(bal));
        when(usageRepo.findByUserIdAndGroupIdAndPeriodIdAndResourceType(1L, 5L, 1L, ResourceType.TRAFFIC))
            .thenReturn(Optional.of(usage));

        svc.saveConsumptionEvent("e", 1L, "TRAFFIC", 50.0, now, 5L);

        assertEquals(100.0, usage.getUsedValue());
        assertEquals(490.0, bal.getRemainingTraffic());
        assertTrue(ul.getIsBlocked());
    }

    @Test void blocksAllGroupMembersWhenGroupBalanceExhausted() {
        setupCommonStubs();
        UserLimitEntity ul = limit(1L, 5L, ResourceType.TRAFFIC, 1000.0, false);
        GroupPackageBalanceEntity bal = balance(5L, 1L, 5.0, 1000L);
        UserLimitEntity other = limit(2L, 5L, ResourceType.TRAFFIC, 100.0, false);
        when(limitRepo.findByUserIdAndGroupIdAndResourceType(1L, 5L, ResourceType.TRAFFIC)).thenReturn(Optional.of(ul));
        when(subscriptionRepo.findActiveByGroupId(eq(5L), any())).thenReturn(Optional.of(sub(5L)));
        when(balanceRepo.findByGroupIdAndPeriodIdForUpdate(5L, 1L)).thenReturn(Optional.of(bal));
        when(usageRepo.findByUserIdAndGroupIdAndPeriodIdAndResourceType(1L, 5L, 1L, ResourceType.TRAFFIC))
            .thenReturn(Optional.empty());
        when(limitRepo.findAllByGroupIdAndResourceType(5L, ResourceType.TRAFFIC)).thenReturn(List.of(ul, other));

        svc.saveConsumptionEvent("e", 1L, "TRAFFIC", 100.0, now, 5L);

        assertTrue(ul.getIsBlocked());
        assertTrue(other.getIsBlocked());
        assertEquals(0.0, bal.getRemainingTraffic());
    }

    @Test void deductsMinutesUpdatingLongBalanceField() {
        setupCommonStubs();
        UserLimitEntity ul = limit(1L, 5L, ResourceType.MINUTES, 100.0, false);
        GroupPackageBalanceEntity bal = balance(5L, 1L, 1000.0, 500L);
        when(limitRepo.findByUserIdAndGroupIdAndResourceType(1L, 5L, ResourceType.MINUTES)).thenReturn(Optional.of(ul));
        when(subscriptionRepo.findActiveByGroupId(eq(5L), any())).thenReturn(Optional.of(sub(5L)));
        when(balanceRepo.findByGroupIdAndPeriodIdForUpdate(5L, 1L)).thenReturn(Optional.of(bal));
        when(usageRepo.findByUserIdAndGroupIdAndPeriodIdAndResourceType(1L, 5L, 1L, ResourceType.MINUTES))
            .thenReturn(Optional.empty());

        svc.saveConsumptionEvent("e", 1L, "MINUTES", 30.0, now, 5L);

        assertEquals(470L, bal.getRemainingMinutes());
        ArgumentCaptor<UserUsageEntity> uc = ArgumentCaptor.forClass(UserUsageEntity.class);
        verify(usageRepo).save(uc.capture());
        assertEquals(ResourceType.MINUTES, uc.getValue().getResourceType());
        assertEquals(30.0, uc.getValue().getUsedValue());
    }

    @Test void skipsDeductionButSavesEventWhenBalanceMissing() {
        setupCommonStubs();
        UserLimitEntity ul = limit(1L, 5L, ResourceType.TRAFFIC, 100.0, false);
        when(limitRepo.findByUserIdAndGroupIdAndResourceType(1L, 5L, ResourceType.TRAFFIC)).thenReturn(Optional.of(ul));
        when(subscriptionRepo.findActiveByGroupId(eq(5L), any())).thenReturn(Optional.of(sub(5L)));
        when(balanceRepo.findByGroupIdAndPeriodIdForUpdate(5L, 1L)).thenReturn(Optional.empty());

        svc.saveConsumptionEvent("e", 1L, "TRAFFIC", 10.0, now, 5L);

        verify(eventRepo).save(any());
        verify(usageRepo, never()).save(any());
    }

    @Test void blocksUserWhenPersonalLimitAlreadyExhaustedBeforeDeduction() {
        setupCommonStubs();
        UserLimitEntity ul = limit(1L, 5L, ResourceType.TRAFFIC, 100.0, false);
        GroupPackageBalanceEntity bal = balance(5L, 1L, 500.0, 1000L);
        UserUsageEntity usage = new UserUsageEntity();
        usage.setUsedValue(100.0);
        when(limitRepo.findByUserIdAndGroupIdAndResourceType(1L, 5L, ResourceType.TRAFFIC)).thenReturn(Optional.of(ul));
        when(subscriptionRepo.findActiveByGroupId(eq(5L), any())).thenReturn(Optional.of(sub(5L)));
        when(balanceRepo.findByGroupIdAndPeriodIdForUpdate(5L, 1L)).thenReturn(Optional.of(bal));
        when(usageRepo.findByUserIdAndGroupIdAndPeriodIdAndResourceType(1L, 5L, 1L, ResourceType.TRAFFIC))
            .thenReturn(Optional.of(usage));

        svc.saveConsumptionEvent("e", 1L, "TRAFFIC", 10.0, now, 5L);

        assertTrue(ul.getIsBlocked());
        assertEquals(500.0, bal.getRemainingTraffic());
    }

    @Test void calculatesPeriodNumberFromCurrentVirtualTime() {
        setupCommonStubs();
        OffsetDateTime event = OffsetDateTime.of(2026, 3, 15, 12, 0, 0, 0, ZoneOffset.UTC);
        UserLimitEntity ul = limit(1L, 5L, ResourceType.TRAFFIC, 100.0, false);
        GroupPackageBalanceEntity bal = balance(5L, 1L, 500.0, 1000L);
        when(limitRepo.findByUserIdAndGroupIdAndResourceType(1L, 5L, ResourceType.TRAFFIC)).thenReturn(Optional.of(ul));
        when(subscriptionRepo.findActiveByGroupId(eq(5L), any())).thenReturn(Optional.of(sub(5L)));
        when(balanceRepo.findByGroupIdAndPeriodIdForUpdate(5L, 1L)).thenReturn(Optional.of(bal));
        when(usageRepo.findByUserIdAndGroupIdAndPeriodIdAndResourceType(1L, 5L, 1L, ResourceType.TRAFFIC))
            .thenReturn(Optional.empty());

        svc.saveConsumptionEvent("e", 1L, "TRAFFIC", 10.0, event, 5L);

        ArgumentCaptor<UserUsageEntity> uc = ArgumentCaptor.forClass(UserUsageEntity.class);
        verify(usageRepo).save(uc.capture());
        assertEquals(1L, uc.getValue().getPeriodId());

        ArgumentCaptor<ConsumptionEventEntity> ec = ArgumentCaptor.forClass(ConsumptionEventEntity.class);
        verify(eventRepo).save(ec.capture());
        assertEquals(event, ec.getValue().getEventTimestamp());
        assertEquals(now.toInstant(), ec.getValue().getProcessedAt().toInstant());
    }
    
    @Test void retroactiveEventDeductsInCurrentPeriodAndBlocksWhenLimitExceeded() {
        setupCommonStubs();
        OffsetDateTime april = OffsetDateTime.of(2026, 4, 10, 12, 0, 0, 0, ZoneOffset.UTC);
        OffsetDateTime marchEvent = OffsetDateTime.of(2026, 3, 20, 15, 0, 0, 0, ZoneOffset.UTC);
        when(clock.instant()).thenReturn(april.toInstant());

        UserLimitEntity ul = limit(1L, 5L, ResourceType.TRAFFIC, 100.0, false);
        GroupPackageBalanceEntity bal = balance(5L, 4L, 500.0, 1000L);
        UserUsageEntity usage = new UserUsageEntity();
        usage.setUserId(1L); usage.setGroupId(5L); usage.setResourceType(ResourceType.TRAFFIC);
        usage.setUsedValue(90.0); usage.setPeriodId(4L);

        when(limitRepo.findByUserIdAndGroupIdAndResourceType(1L, 5L, ResourceType.TRAFFIC)).thenReturn(Optional.of(ul));
        when(subscriptionRepo.findActiveByGroupId(eq(5L), any())).thenReturn(Optional.of(sub(5L)));
        when(balanceRepo.findByGroupIdAndPeriodIdForUpdate(5L, 4L)).thenReturn(Optional.of(bal));
        when(usageRepo.findByUserIdAndGroupIdAndPeriodIdAndResourceType(1L, 5L, 4L, ResourceType.TRAFFIC))
            .thenReturn(Optional.of(usage));

        svc.saveConsumptionEvent("late-event", 1L, "TRAFFIC", 50.0, marchEvent, 5L);

        assertEquals(100.0, usage.getUsedValue());
        assertEquals(490.0, bal.getRemainingTraffic());
        assertTrue(ul.getIsBlocked());

        ArgumentCaptor<ConsumptionEventEntity> ec = ArgumentCaptor.forClass(ConsumptionEventEntity.class);
        verify(eventRepo).save(ec.capture());
        assertEquals(marchEvent, ec.getValue().getEventTimestamp());
        assertEquals(april.toInstant(), ec.getValue().getProcessedAt().toInstant());
    }
}
