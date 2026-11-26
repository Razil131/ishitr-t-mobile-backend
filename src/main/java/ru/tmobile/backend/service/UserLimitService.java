package ru.tmobile.backend.service;

import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import ru.tmobile.backend.dto.familyGroup.UserLimitResponse;
import ru.tmobile.backend.entity.AuditLogEntity.ActionType;
import ru.tmobile.backend.entity.GroupMemberEntity.MemberStatus;
import ru.tmobile.backend.entity.FamilyGroupEntity;
import ru.tmobile.backend.entity.FamilyGroupEntity.GroupStatus;
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

@Service
@RequiredArgsConstructor
public class UserLimitService {
    private final UserLimitRepository userLimitRepository;
    private final UserUsageRepository userUsageRepository;
    private final GroupSubscriptionRepository groupSubscriptionRepository;
    private final ServicePackageRepository servicePackageRepository;
    private final FamilyGroupRepository familyGroupRepository;
    private final GroupMemberRepository groupMemberRepository;
    private final VirtualClockService virtualClock;
    private final GroupAuditService auditService;

    private static final YearMonth BASE_PERIOD = YearMonth.of(2026, 1);

    @Transactional
    public UserLimitResponse setMemberLimit(Long groupId, Long userId,
                            String resourceType, Double limitValue, Long callerId) {
        FamilyGroupEntity group = familyGroupRepository.findById(groupId)
            .orElseThrow(() -> new BusinessRuleViolationException("GROUP_NOT_FOUND", "Group not found", HttpStatus.NOT_FOUND));
        if (group.getStatus() != GroupStatus.ACTIVE) {
            throw new BusinessRuleViolationException("GROUP_DISBANDED", "Group is disbanded", HttpStatus.CONFLICT);
        }
        validateOwnerAccess(group, callerId);

        ResourceType type;
        try {
            type = ResourceType.valueOf(resourceType);
        } catch (IllegalArgumentException e) {
            throw new BusinessRuleViolationException("INVALID_RESOURCE_TYPE", "Allowed: TRAFFIC, MINUTES", HttpStatus.BAD_REQUEST);
        }

        groupMemberRepository
            .findByGroupIdAndUserIdAndStatus(groupId, userId, MemberStatus.ACTIVE)
            .orElseThrow(() -> new BusinessRuleViolationException("MEMBER_NOT_ACTIVE", "Target member is not active", HttpStatus.CONFLICT));

        OffsetDateTime now = virtualClock.instant().atZone(ZoneId.systemDefault()).toOffsetDateTime();
        double totalValue = groupSubscriptionRepository.findActiveByGroupId(groupId, now)
            .flatMap(sub -> servicePackageRepository.findById(sub.getPackageId()))
            .map(pkg -> type == ResourceType.TRAFFIC ? pkg.getTotalTraffic() : pkg.getTotalMinutes().doubleValue())
            .orElse(0d);

        double minAllowed = totalValue * 0.05;
        double maxAllowed = totalValue;
        if (limitValue == null || limitValue < minAllowed - 1e-9 || limitValue > maxAllowed + 1e-9) {
            throw new BusinessRuleViolationException("INVALID_LIMIT_VALUE", "limit_value must be in [5%, 100%] of package total", HttpStatus.BAD_REQUEST);
        }

        long periodId = ChronoUnit.MONTHS.between(BASE_PERIOD, YearMonth.from(now)) + 1;
        double used = userUsageRepository
            .findByUserIdAndGroupIdAndPeriodIdAndResourceType(userId, groupId, periodId, type)
            .map(UserUsageEntity::getUsedValue)
            .orElse(0d);

        UserLimitEntity limit = userLimitRepository
            .findByUserIdAndGroupIdAndResourceType(userId, groupId, type)
            .orElseGet(() -> {
                UserLimitEntity n = new UserLimitEntity();
                n.setUserId(userId);
                n.setGroupId(groupId);
                n.setResourceType(type);
                return n;
            });

        limit.setLimitValue(limitValue);
        boolean blocked = limitValue < used - 1e-9;
        limit.setIsBlocked(blocked);
        userLimitRepository.save(limit);

        Map<String, Object> details = Map.of(
            "target_user_id", userId,
            "resource_type", type.name(),
            "limit_value", limitValue
        );
        auditService.save(auditService.log(groupId, callerId, userId, ActionType.LIMIT_CHANGED, now, details));

        String status = blocked ? "BLOCKED"
            : (Double.compare(limitValue, maxAllowed) == 0 ? "NOT_SET" : "ACTIVE");

        return new UserLimitResponse(userId, groupId, type.name(), limitValue, used, status, minAllowed, maxAllowed);
    }

    @Transactional
    public void invalidateMemberLimits(Long groupId, Long userId) {
        List<UserLimitEntity> limits = new ArrayList<>();
        for (ResourceType type : ResourceType.values()) {
            userLimitRepository.findByUserIdAndGroupIdAndResourceType(userId, groupId, type)
                .ifPresent(limits::add);
        }
        for (UserLimitEntity limit : limits) {
            limit.setLimitValue(0.0);
            limit.setIsBlocked(true);
        }
        if (!limits.isEmpty()) {
            userLimitRepository.saveAll(limits);
        }
    }

    private void validateOwnerAccess(FamilyGroupEntity group, Long callerId) {
        if (!group.getOwnerId().equals(callerId)) {
            throw new BusinessRuleViolationException("FORBIDDEN", "Only group owner can perform this action", HttpStatus.FORBIDDEN);
        }
    }
}
