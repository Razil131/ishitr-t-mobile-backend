package ru.tmobile.backend.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.tmobile.backend.entity.ConsumptionEventEntity;
import ru.tmobile.backend.entity.GroupPackageBalanceEntity;
import ru.tmobile.backend.entity.GroupSubscriptionEntity;
import ru.tmobile.backend.entity.UserLimitEntity;
import ru.tmobile.backend.entity.UserUsageEntity;
import ru.tmobile.backend.entity.enums.ResourceType;
import ru.tmobile.backend.repository.ConsumptionEventRepository;
import ru.tmobile.backend.repository.GroupPackageBalanceRepository;
import ru.tmobile.backend.repository.GroupSubscriptionRepository;
import ru.tmobile.backend.repository.UserLimitRepository;
import ru.tmobile.backend.repository.UserUsageRepository;
import ru.tmobile.backend.virtual_clock.VirtualClockService;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ConsumptionEventService {

    private final UserUsageRepository userUsageRepository;
    private final ConsumptionEventRepository consumptionEventRepository;
    private final UserLimitRepository userLimitRepository;
    private final GroupPackageBalanceRepository groupPackageBalanceRepository;
    private final GroupSubscriptionRepository groupSubscriptionRepository;
    private final VirtualClockService virtualClockService;

    private static final java.time.YearMonth BASE_PERIOD = java.time.YearMonth.of(2026,1);

    @Transactional
    public void saveConsumptionEvent(
            String eventId,
            Long userId,
            String resourceType,
            Double amount,
            OffsetDateTime eventTimestamp,
            Long groupId
    ) {
        if (consumptionEventRepository.existsByEventId(eventId)) {
            log.warn("Duplicate event ignored: eventId={}", eventId);
            return;
        }

        if (amount == null || amount <= 0){
            log.warn("Invalid amount={} for eventId={}, skipping",amount, eventId);
            return;
        }

        OffsetDateTime now = virtualClockService.instant()
            .atZone(ZoneId.systemDefault())
            .toOffsetDateTime();

        if (eventTimestamp == null) {
            log.warn("Event timestamp is null, using current time: eventId={}", eventId);
            eventTimestamp = now;
        }

        Long periodNumber = java.time.temporal.ChronoUnit.MONTHS.between(
            BASE_PERIOD,
            java.time.YearMonth.from(now)
        ) + 1;

        ResourceType limitResType = ResourceType.valueOf(resourceType);

        UserLimitEntity userLimit = userLimitRepository
            .findByUserIdAndGroupIdAndResourceType(userId, groupId, limitResType)
            .orElse(null);

        boolean canDeduct = isValidConsumption(groupId, eventId, userId, limitResType, now, periodNumber, userLimit);
        if (canDeduct && userLimit != null) {
            deductResource(userId, groupId, eventId, amount, periodNumber, limitResType, userLimit);
        }

        ConsumptionEventEntity consumptionEvent = new ConsumptionEventEntity();
        consumptionEvent.setEventId(eventId);
        consumptionEvent.setUserId(userId);
        consumptionEvent.setResourceType(limitResType);
        consumptionEvent.setAmount(amount);
        consumptionEvent.setEventTimestamp(eventTimestamp);
        consumptionEvent.setProcessedAt(now);
        consumptionEvent.setGroupId(groupId);
        consumptionEventRepository.save(consumptionEvent);
        log.info("Saved consumption event: id={}, user={}, type={}", eventId, userId, resourceType);
    }

    private void deductResource(Long userId, Long groupId, String eventId, Double amount, Long periodNumber, ResourceType limitResType, UserLimitEntity userLimit){
        GroupPackageBalanceEntity balance = groupPackageBalanceRepository
            .findByGroupIdAndPeriodIdForUpdate(groupId, periodNumber).orElse(null);

        if (balance == null) {
            log.warn("No group balance for group={}, period={}: eventId={}", groupId, periodNumber, eventId);
            return;
        }

        UserUsageEntity userUsage = userUsageRepository
            .findByUserIdAndGroupIdAndPeriodIdAndResourceType(userId,groupId,periodNumber,limitResType)
            .orElse(null);

        Double currentUsed = (userUsage != null)? userUsage.getUsedValue() : 0.0;

        double currentRemaining;
        if (limitResType == ResourceType.TRAFFIC) {
            currentRemaining = balance.getRemainingTraffic();
        } else {
            currentRemaining = balance.getRemainingMinutes().doubleValue();
        }

        double personalAvailable = Math.max(0.0, userLimit.getLimitValue() - currentUsed);
        double effectiveAmount = Math.min(amount, personalAvailable);
        double deductible = Math.min(currentRemaining, effectiveAmount);
        
        if (deductible <= 0) {
            if (personalAvailable <= 0) {
                userLimit.setIsBlocked(true);
                userLimitRepository.save(userLimit);
                log.info("User {} blocked for {} (personal limit reached before deduction): eventId={}",
                    userId, limitResType, eventId);
            } else {
                log.warn("Group balance is zero for group={}, period={}, type={}: eventId={}",
                    groupId, periodNumber, limitResType, eventId);
                blockAllGroupMembers(groupId, limitResType, eventId);
            }
            return;
        }

        if (limitResType == ResourceType.TRAFFIC) {
            balance.setRemainingTraffic(balance.getRemainingTraffic() - deductible);
        } else {
            balance.setRemainingMinutes(balance.getRemainingMinutes() - (long) deductible);
        }

        groupPackageBalanceRepository.save(balance);

        if (userUsage != null){
            userUsage.setUsedValue(userUsage.getUsedValue() + deductible);
        }else{
            userUsage = createUserUsage(userId, groupId, limitResType, deductible, periodNumber);
        }

        userUsageRepository.save(userUsage);

        if (userUsage.getUsedValue() >= userLimit.getLimitValue()) {
            userLimit.setIsBlocked(true);
            userLimitRepository.save(userLimit);
            log.info("User {} blocked for {} (personal limit reached): eventId={}",
                userId, limitResType, eventId);
        }

        if (deductible < effectiveAmount) {
            log.warn("Group balance exhausted for group={}, period={}, type={}: deducted {} of {}, eventId={}",
                groupId, periodNumber, limitResType, deductible, effectiveAmount, eventId);
            blockAllGroupMembers(groupId, limitResType, eventId);
        }
    }

    private void blockAllGroupMembers(Long groupId, ResourceType resourceType, String eventId){
        List<UserLimitEntity> allGroupLimits = userLimitRepository
            .findAllByGroupIdAndResourceType(groupId, resourceType);
        for (UserLimitEntity limit : allGroupLimits) {
            if (!Boolean.TRUE.equals(limit.getIsBlocked())) {
                limit.setIsBlocked(true);
                userLimitRepository.save(limit);
                log.info("Blocked user {} for {} due to group balance exhaustion: eventId={}",
                    limit.getUserId(), resourceType, eventId);
            }
        }
    }

    private UserUsageEntity createUserUsage(Long userId, Long groupId, ResourceType resourceType, Double usedValue, Long periodId){
        var userUsage = new UserUsageEntity();
        userUsage.setUserId(userId);
        userUsage.setGroupId(groupId);
        userUsage.setResourceType(resourceType);
        userUsage.setUsedValue(usedValue);
        userUsage.setPeriodId(periodId);
        return userUsage;
    }

    private boolean isValidConsumption(Long groupId, String eventId, Long userId, ResourceType limitResType, OffsetDateTime now, Long periodNumber, UserLimitEntity userLimit){
        if (groupId == null){
            log.info("Event saved without group context: eventId={}, userId={}", eventId, userId);
            return false;
        }
        
        GroupSubscriptionEntity subscription = groupSubscriptionRepository
            .findActiveByGroupId(groupId, now).orElse(null);

        if (subscription == null){
            log.warn("No active subscription for group={}, event not deducted: eventId={}", groupId, eventId);
            return false;
        }

        if (userLimit != null && Boolean.TRUE.equals(userLimit.getIsBlocked())){
            log.info("User {} already blocked for {}, event not deducted: eventId={}", 
                userId, limitResType, eventId);
            return false;
        }

        return true;
    }
}