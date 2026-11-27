package ru.tmobile.backend.service;

import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import ru.tmobile.backend.dto.familyGroup.GroupDetailResponse;
import ru.tmobile.backend.dto.familyGroup.GroupLimitResponse;
import ru.tmobile.backend.dto.familyGroup.GroupMemberDetail;
import ru.tmobile.backend.dto.familyGroup.MemberLimits;
import ru.tmobile.backend.dto.familyGroup.PackageInfo;
import ru.tmobile.backend.dto.familyGroup.UserLimitDetail;
import ru.tmobile.backend.entity.FamilyGroupEntity;
import ru.tmobile.backend.entity.GroupMemberEntity;
import ru.tmobile.backend.entity.GroupMemberEntity.MemberStatus;
import ru.tmobile.backend.entity.UserEntity;
import ru.tmobile.backend.entity.UserLimitEntity;
import ru.tmobile.backend.entity.UserUsageEntity;
import ru.tmobile.backend.entity.enums.ResourceType;
import ru.tmobile.backend.exception.BusinessRuleViolationException;
import ru.tmobile.backend.repository.GroupMemberRepository;
import ru.tmobile.backend.repository.GroupSubscriptionRepository;
import ru.tmobile.backend.repository.ServicePackageRepository;
import ru.tmobile.backend.repository.UserLimitRepository;
import ru.tmobile.backend.repository.UserRepository;
import ru.tmobile.backend.repository.UserUsageRepository;
import ru.tmobile.backend.virtual_clock.VirtualClockService;

@Service
@RequiredArgsConstructor
public class GroupDetailsService {
    private final GroupMemberRepository memberRepository;
    private final UserRepository userRepository;
    private final UserLimitRepository userLimitRepository;
    private final UserUsageRepository userUsageRepository;
    private final GroupSubscriptionRepository groupSubscriptionRepository;
    private final ServicePackageRepository servicePackageRepository;
    private final VirtualClockService virtualClockService;

    private static final YearMonth BASE_PERIOD = YearMonth.of(2026, 1);

    public GroupLimitResponse toLimitResponse(FamilyGroupEntity group, Long callerId) {
        OffsetDateTime now = virtualClockService.instant().atZone(ZoneId.systemDefault()).toOffsetDateTime();
        long period = ChronoUnit.MONTHS.between(BASE_PERIOD, YearMonth.from(now)) +1;

        boolean isOwner = group.getOwnerId().equals(callerId);
        List<GroupMemberEntity> members = memberRepository.findByGroupIdAndStatus(group.getId(), MemberStatus.ACTIVE)
            .stream().filter(m -> isOwner || m.getUserId().equals(callerId))
            .toList();

        if (members.isEmpty()) {
            throw new BusinessRuleViolationException("FORBIDDEN", "User is not a member of group", HttpStatus.FORBIDDEN);
        }

        Map<Long, UserEntity> users = userRepository
            .findByIdIn(members.stream().map(GroupMemberEntity::getUserId).toList())
            .stream().collect(Collectors.toMap(UserEntity::getId, u->u));

        Map<String, UserLimitEntity> limitsByKeyMap = userLimitRepository.findAllByGroupId(group.getId())
            .stream()
            .collect(Collectors.toMap(
                l -> key(l.getUserId(), l.getResourceType()),
                l -> l,
                (a,b) -> a));
        
        Map<String, Double> usedByKey = userUsageRepository.findAllByGroupIdAndPeriodId(group.getId(), period)
            .stream()
            .collect(Collectors.toMap(
                u -> key(u.getUserId(), u.getResourceType()),
            UserUsageEntity::getUsedValue,
            (a,b) -> a));
        
        PackageInfo pkg = buildPackageInfo(group.getId(), now);
        double totalTraffic = pkg != null ? pkg.totalTrafficGb() : 0d;
        double totalMinutes = pkg != null ? pkg.totalMinutes() : 0d;

        List<MemberLimits> result = members.stream()
            .map(m -> new MemberLimits(
                m.getUserId(),
                users.get(m.getUserId()) != null ? users.get(m.getUserId()).getPhoneNumber() : null,
                users.get(m.getUserId()) != null ? users.get(m.getUserId()).getName() : null,
                m.getRole().name(),
                List.of(
                    toLimitDetail(m.getUserId(), ResourceType.TRAFFIC, limitsByKeyMap, usedByKey, totalTraffic),
                    toLimitDetail(m.getUserId(), ResourceType.MINUTES, limitsByKeyMap, usedByKey, totalMinutes)
                ))).toList();
        
        return new GroupLimitResponse(group.getId(), result);
    }

    public GroupDetailResponse toDetailResponse(FamilyGroupEntity group) {
        OffsetDateTime now = virtualClockService.instant().atZone(ZoneId.systemDefault()).toOffsetDateTime();
        long periodId = ChronoUnit.MONTHS.between(BASE_PERIOD, YearMonth.from(now)) + 1;

        List<GroupMemberEntity> activeMembers =
            memberRepository.findByGroupIdAndStatus(group.getId(), GroupMemberEntity.MemberStatus.ACTIVE);
        Map<Long, UserEntity> users = userRepository
            .findByIdIn(activeMembers.stream().map(GroupMemberEntity::getUserId).toList())
            .stream()
            .collect(Collectors.toMap(UserEntity::getId, u -> u));

        Map<String, UserLimitEntity> limitsByKey = userLimitRepository.findAllByGroupId(group.getId()).stream()
            .collect(Collectors.toMap(
                l -> key(l.getUserId(), l.getResourceType()),
                l -> l,
                (a, b) -> a));
        Map<String, Double> usedByKey = userUsageRepository.findAllByGroupIdAndPeriodId(group.getId(), periodId).stream()
            .collect(Collectors.toMap(
                u -> key(u.getUserId(), u.getResourceType()),
                UserUsageEntity::getUsedValue,
                (a, b) -> a));

        PackageInfo pkg = buildPackageInfo(group.getId(), now);
        double totalTraffic = pkg != null ? pkg.totalTrafficGb() : 0d;
        double totalMinutes = pkg != null ? pkg.totalMinutes().doubleValue() : 0d;

        List<GroupMemberDetail> members = activeMembers.stream()
            .map(m -> toMemberDetail(m, users.get(m.getUserId()), limitsByKey, usedByKey, totalTraffic, totalMinutes))
            .toList();

        return new GroupDetailResponse(
            group.getId(),
            null,
            group.getOwnerId(),
            group.getStatus(),
            group.getCreatedAt(),
            members.size(),
            null,
            members,
            pkg
        );
    }

    private GroupMemberDetail toMemberDetail(GroupMemberEntity member, UserEntity user,
            Map<String, UserLimitEntity> limitsByKey, Map<String, Double> usedByKey,
            double totalTraffic, double totalMinutes) {
        List<UserLimitDetail> limits = List.of(
            toLimitDetail(member.getUserId(), ResourceType.TRAFFIC, limitsByKey, usedByKey, totalTraffic),
            toLimitDetail(member.getUserId(), ResourceType.MINUTES, limitsByKey, usedByKey, totalMinutes));

        String status = switch (member.getStatus()) {
            case ACTIVE -> "ACTIVE";
            case EXCLUDED -> "EXCLUDED";
            case LEAVE -> "LEFT";
        };

        return new GroupMemberDetail(
            member.getUserId(),
            user != null ? user.getPhoneNumber() : null,
            user != null ? user.getName() : null,
            member.getRole().name(),
            status,
            member.getJoinedAt(),
            null,
            limits);
    }

    private UserLimitDetail toLimitDetail(Long userId, ResourceType type,
            Map<String, UserLimitEntity> limitsByKey, Map<String, Double> usedByKey, double totalValue) {
        String k = key(userId, type);
        UserLimitEntity limit = limitsByKey.get(k);
        double used = usedByKey.getOrDefault(k, 0d);

        if (limit == null) {
            return new UserLimitDetail(type.name(), totalValue, used, "NOT_SET");
        }

        String status = Boolean.TRUE.equals(limit.getIsBlocked()) ? "BLOCKED"
            : (Double.compare(limit.getLimitValue(), totalValue) == 0 ? "NOT_SET" : "ACTIVE");
        return new UserLimitDetail(type.name(), limit.getLimitValue(), used, status);
    }

    private PackageInfo buildPackageInfo(Long groupId, OffsetDateTime now) {
        return groupSubscriptionRepository.findActiveByGroupId(groupId, now)
            .flatMap(sub -> servicePackageRepository.findById(sub.getPackageId())
                .map(pkg -> new PackageInfo(
                    pkg.getId(),
                    pkg.getName(),
                    pkg.getTotalTraffic(),
                    pkg.getTotalMinutes(),
                    sub.getPeriodStart(),
                    sub.getPeriodEnd())))
            .orElse(null);
    }

    private static String key(Long userId, ResourceType type) {
        return userId + ":" + type.name();
    }
}
