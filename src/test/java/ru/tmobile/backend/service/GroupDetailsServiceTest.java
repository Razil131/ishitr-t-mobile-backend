package ru.tmobile.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import ru.tmobile.backend.dto.familyGroup.GroupDetailResponse;
import ru.tmobile.backend.dto.familyGroup.GroupMemberDetail;
import ru.tmobile.backend.dto.familyGroup.UserLimitDetail;
import ru.tmobile.backend.entity.FamilyGroupEntity;
import ru.tmobile.backend.entity.GroupMemberEntity;
import ru.tmobile.backend.entity.GroupMemberEntity.MemberRole;
import ru.tmobile.backend.entity.GroupMemberEntity.MemberStatus;
import ru.tmobile.backend.entity.GroupSubscriptionEntity;
import ru.tmobile.backend.entity.ServicePackageEntity;
import ru.tmobile.backend.entity.UserEntity;
import ru.tmobile.backend.entity.UserLimitEntity;
import ru.tmobile.backend.entity.UserUsageEntity;
import ru.tmobile.backend.entity.enums.ResourceType;
import ru.tmobile.backend.repository.GroupMemberRepository;
import ru.tmobile.backend.repository.GroupSubscriptionRepository;
import ru.tmobile.backend.repository.ServicePackageRepository;
import ru.tmobile.backend.repository.UserLimitRepository;
import ru.tmobile.backend.repository.UserRepository;
import ru.tmobile.backend.repository.UserUsageRepository;
import ru.tmobile.backend.virtual_clock.VirtualClockService;

class GroupDetailsServiceTest {
    GroupMemberRepository memberRepository;
    UserRepository userRepository;
    UserLimitRepository userLimitRepository;
    UserUsageRepository userUsageRepository;
    GroupSubscriptionRepository groupSubscriptionRepository;
    ServicePackageRepository servicePackageRepository;
    VirtualClockService virtualClockService;
    GroupDetailsService service;

    @BeforeEach void up() {
        memberRepository = mock(GroupMemberRepository.class);
        userRepository = mock(UserRepository.class);
        userLimitRepository = mock(UserLimitRepository.class);
        userUsageRepository = mock(UserUsageRepository.class);
        groupSubscriptionRepository = mock(GroupSubscriptionRepository.class);
        servicePackageRepository = mock(ServicePackageRepository.class);
        virtualClockService = mock(VirtualClockService.class);
        service = new GroupDetailsService(
            memberRepository, userRepository, userLimitRepository, userUsageRepository,
            groupSubscriptionRepository, servicePackageRepository, virtualClockService);
    }

    private FamilyGroupEntity group() {
        FamilyGroupEntity g = new FamilyGroupEntity();
        g.setId(10L);
        g.setOwnerId(1L);
        g.setStatus(FamilyGroupEntity.GroupStatus.ACTIVE);
        g.setCreatedAt(OffsetDateTime.of(2026, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC));
        return g;
    }

    private GroupMemberEntity member(long userId, MemberRole role, MemberStatus status) {
        GroupMemberEntity m = new GroupMemberEntity();
        m.setGroupId(10L);
        m.setUserId(userId);
        m.setRole(role);
        m.setStatus(status);
        m.setJoinedAt(OffsetDateTime.of(2026, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC));
        return m;
    }

    private UserEntity user(long id, String phone, String name) {
        UserEntity u = new UserEntity();
        u.setId(id);
        u.setPhoneNumber(phone);
        u.setName(name);
        return u;
    }

    private UserLimitEntity limit(long userId, ResourceType type, double value, boolean blocked) {
        UserLimitEntity l = new UserLimitEntity();
        l.setUserId(userId);
        l.setGroupId(10L);
        l.setResourceType(type);
        l.setLimitValue(value);
        l.setIsBlocked(blocked);
        return l;
    }

    private UserUsageEntity usage(long userId, ResourceType type, double used) {
        UserUsageEntity u = new UserUsageEntity();
        u.setUserId(userId);
        u.setGroupId(10L);
        u.setResourceType(type);
        u.setPeriodId(1L);
        u.setUsedValue(used);
        return u;
    }

    private void stubPackage(double traffic, long minutes) {
        OffsetDateTime start = OffsetDateTime.of(2026, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC);
        OffsetDateTime end = OffsetDateTime.of(2026, 2, 1, 0, 0, 0, 0, ZoneOffset.UTC);
        GroupSubscriptionEntity sub = new GroupSubscriptionEntity();
        sub.setGroupId(10L);
        sub.setPackageId(100L);
        sub.setPeriodStart(start);
        sub.setPeriodEnd(end);

        ServicePackageEntity pkg = new ServicePackageEntity();
        pkg.setId(100L);
        pkg.setName("Family");
        pkg.setTotalTraffic(traffic);
        pkg.setTotalMinutes(minutes);

        when(groupSubscriptionRepository.findActiveByGroupId(eq(10L), any())).thenReturn(Optional.of(sub));
        when(servicePackageRepository.findById(100L)).thenReturn(Optional.of(pkg));
    }

    private UserLimitDetail findLimit(GroupMemberDetail member, ResourceType type) {
        return member.limits().stream()
            .filter(l -> l.resourceType().equals(type.name()))
            .findFirst()
            .orElseThrow();
    }

    @Test void buildsGroupDetailWithMembersLimitsAndPackage() {
        when(virtualClockService.instant()).thenReturn(
            OffsetDateTime.of(2026, 1, 15, 0, 0, 0, 0, ZoneOffset.UTC).toInstant());
        when(memberRepository.findByGroupIdAndStatus(10L, MemberStatus.ACTIVE))
            .thenReturn(List.of(
                member(1L, MemberRole.OWNER, MemberStatus.ACTIVE),
                member(2L, MemberRole.MEMBER, MemberStatus.ACTIVE)));
        when(userRepository.findByIdIn(List.of(1L, 2L)))
            .thenReturn(List.of(user(1L, "+7001", "Иван"), user(2L, "+7002", "Пётр")));
        stubPackage(50d, 1000L);
        when(userLimitRepository.findAllByGroupId(10L)).thenReturn(List.of(
            limit(1L, ResourceType.TRAFFIC, 20d, false),
            limit(1L, ResourceType.MINUTES, 1000L, true),
            limit(2L, ResourceType.TRAFFIC, 50d, false)));
        when(userUsageRepository.findAllByGroupIdAndPeriodId(10L, 1L)).thenReturn(List.of(
            usage(1L, ResourceType.TRAFFIC, 12.5),
            usage(2L, ResourceType.MINUTES, 300d)));

        GroupDetailResponse result = service.toDetailResponse(group());

        assertThat(result.groupId()).isEqualTo(10L);
        assertThat(result.OwnerId()).isEqualTo(1L);
        assertThat(result.status()).isEqualTo(FamilyGroupEntity.GroupStatus.ACTIVE);
        assertThat(result.memberCount()).isEqualTo(2);
        assertThat(result.name()).isNull();
        assertThat(result.transferPending()).isNull();
        assertThat(result.packageInfo()).isNotNull();
        assertThat(result.packageInfo().packageId()).isEqualTo(100L);
        assertThat(result.packageInfo().totalTrafficGb()).isEqualTo(50d);
        assertThat(result.packageInfo().totalMinutes()).isEqualTo(1000L);

        assertThat(result.members()).hasSize(2);
        GroupMemberDetail owner = result.members().get(0);
        assertThat(owner.userId()).isEqualTo(1L);
        assertThat(owner.phoneNumber()).isEqualTo("+7001");
        assertThat(owner.name()).isEqualTo("Иван");
        assertThat(owner.role()).isEqualTo("OWNER");
        assertThat(owner.status()).isEqualTo("ACTIVE");
        assertThat(owner.leftAt()).isNull();

        UserLimitDetail ownerTraffic = findLimit(owner, ResourceType.TRAFFIC);
        assertThat(ownerTraffic.status()).isEqualTo("ACTIVE");
        assertThat(ownerTraffic.limitValue()).isEqualTo(20d);
        assertThat(ownerTraffic.usedValue()).isEqualTo(12.5);

        UserLimitDetail ownerMinutes = findLimit(owner, ResourceType.MINUTES);
        assertThat(ownerMinutes.status()).isEqualTo("BLOCKED");
        assertThat(ownerMinutes.limitValue()).isEqualTo(1000d);

        GroupMemberDetail second = result.members().get(1);
        UserLimitDetail noLimit = findLimit(second, ResourceType.MINUTES);
        assertThat(noLimit.status()).isEqualTo("NOT_SET");
        assertThat(noLimit.limitValue()).isEqualTo(1000d);
        assertThat(noLimit.usedValue()).isEqualTo(300d);

        UserLimitDetail fullLimit = findLimit(second, ResourceType.TRAFFIC);
        assertThat(fullLimit.status()).isEqualTo("NOT_SET");
        assertThat(fullLimit.limitValue()).isEqualTo(50d);
    }

    @Test void mapsLeaveStatusAndSkipsMissingPackage() {
        when(virtualClockService.instant()).thenReturn(Instant.parse("2026-04-10T00:00:00Z"));
        when(memberRepository.findByGroupIdAndStatus(10L, MemberStatus.ACTIVE))
            .thenReturn(List.of(member(3L, MemberRole.MEMBER, MemberStatus.LEAVE)));
        when(userRepository.findByIdIn(List.of(3L))).thenReturn(List.of());
        when(userLimitRepository.findAllByGroupId(10L)).thenReturn(List.of());
        when(userUsageRepository.findAllByGroupIdAndPeriodId(10L, 4L)).thenReturn(List.of());
        when(groupSubscriptionRepository.findActiveByGroupId(eq(10L), any())).thenReturn(Optional.empty());

        GroupDetailResponse result = service.toDetailResponse(group());

        assertThat(result.packageInfo()).isNull();
        assertThat(result.members()).hasSize(1);
        assertThat(result.members().get(0).status()).isEqualTo("LEFT");
        assertThat(result.members().get(0).phoneNumber()).isNull();
        assertThat(result.members().get(0).name()).isNull();
        assertThat(findLimit(result.members().get(0), ResourceType.TRAFFIC).limitValue()).isEqualTo(0d);
    }
}
