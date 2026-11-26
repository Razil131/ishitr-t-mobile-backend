package ru.tmobile.backend.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.fasterxml.jackson.databind.ObjectMapper;

import ru.tmobile.backend.dto.familyGroup.AddMemberRequest;
import ru.tmobile.backend.dto.familyGroup.FamilyGroupCreateRequest;
import ru.tmobile.backend.dto.familyGroup.GroupDetailResponse;
import ru.tmobile.backend.dto.familyGroup.LeaveGroupRequest;
import ru.tmobile.backend.dto.familyGroup.SetLimitRequest;
import ru.tmobile.backend.dto.familyGroup.TransferOwnershipRequest;
import ru.tmobile.backend.dto.familyGroup.UserLimitResponse;
import ru.tmobile.backend.entity.FamilyGroupEntity;
import ru.tmobile.backend.entity.FamilyGroupEntity.GroupStatus;
import ru.tmobile.backend.entity.GroupMemberEntity;
import ru.tmobile.backend.entity.GroupMemberEntity.MemberStatus;
import ru.tmobile.backend.entity.UserEntity;
import ru.tmobile.backend.exception.BusinessRuleViolationException;
import ru.tmobile.backend.exception.GlobalExceptionHandler;
import ru.tmobile.backend.repository.FamilyGroupRepository;
import ru.tmobile.backend.repository.GroupMemberRepository;
import ru.tmobile.backend.repository.UserRepository;
import ru.tmobile.backend.security.AuthContext;
import ru.tmobile.backend.service.FamilyGroupService;
import ru.tmobile.backend.service.GroupDetailsService;
import ru.tmobile.backend.service.UserLimitService;

class FamilyGroupControllerTest {
    MockMvc mockMvc;
    FamilyGroupService familyGroupService;
    GroupDetailsService groupDetailsService;
    FamilyGroupRepository groupRepository;
    GroupMemberRepository memberRepository;
    UserRepository userRepository;
    UserLimitService userLimitService;
    ObjectMapper json;

    @BeforeEach void up() {
        familyGroupService = mock(FamilyGroupService.class);
        groupDetailsService = mock(GroupDetailsService.class);
        groupRepository = mock(FamilyGroupRepository.class);
        memberRepository = mock(GroupMemberRepository.class);
        userRepository = mock(UserRepository.class);
        userLimitService = mock(UserLimitService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(
                new FamilyGroupController(
                    familyGroupService, groupDetailsService, groupRepository, memberRepository, userRepository, userLimitService))
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();
        json = new ObjectMapper();
    }

    @AfterEach void cleanAuthContext() {
        AuthContext.clear();
    }

    private FamilyGroupEntity grp(long id, long ownerId) {
        FamilyGroupEntity g = new FamilyGroupEntity();
        g.setId(id); g.setOwnerId(ownerId); g.setStatus(GroupStatus.ACTIVE);
        g.setCreatedAt(OffsetDateTime.of(2026, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC));
        return g;
    }

    private GroupMemberEntity membership(long gid, long uid) {
        GroupMemberEntity m = new GroupMemberEntity();
        m.setGroupId(gid); m.setUserId(uid); m.setStatus(MemberStatus.ACTIVE);
        return m;
    }

    private UserEntity user(long id, String phone, String name) {
        UserEntity u = new UserEntity();
        u.setId(id); u.setPhoneNumber(phone); u.setName(name);
        u.setCreatedAt(OffsetDateTime.now(ZoneOffset.UTC));
        return u;
    }

    private GroupDetailResponse details(long groupId, long ownerId) {
        return new GroupDetailResponse(
            groupId, null, ownerId, GroupStatus.ACTIVE,
            OffsetDateTime.of(2026, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC),
            1, null, List.of(), null);
    }

    @Test void listGroupsReturnsSummaryForAllGroups() throws Exception {
        FamilyGroupEntity g = grp(5L, 1L);
        when(groupRepository.findAll()).thenReturn(List.of(g));
        when(memberRepository.findByGroupIdAndStatus(5L, MemberStatus.ACTIVE))
            .thenReturn(List.of(membership(5L, 1L), membership(5L, 2L)));

        mockMvc.perform(get("/api/v1/groups"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].group_id").value(5))
            .andExpect(jsonPath("$[0].owner_id").value(1))
            .andExpect(jsonPath("$[0].members_count").value(2))
            .andExpect(jsonPath("$[0].status").value("ACTIVE"))
            .andExpect(jsonPath("$[0].owner_name").doesNotExist())
            .andExpect(jsonPath("$[0].name").doesNotExist());
    }

    @Test void getGroupReturnsActiveGroupDetails() throws Exception {
        FamilyGroupEntity g = grp(5L, 1L);
        AuthContext.set(new AuthContext.UserContext(1L, "OWNER"));
        when(groupRepository.findByIdAndStatus(5L, GroupStatus.ACTIVE)).thenReturn(Optional.of(g));
        when(memberRepository.findByGroupIdAndUserIdAndStatus(5L, 1L, MemberStatus.ACTIVE))
            .thenReturn(Optional.of(membership(5L, 1L)));
        when(groupDetailsService.toDetailResponse(g)).thenReturn(details(5L, 1L));

        mockMvc.perform(get("/api/v1/groups/5"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.group_id").value(5))
            .andExpect(jsonPath("$.owner_id").value(1))
            .andExpect(jsonPath("$.status").value("ACTIVE"))
            .andExpect(jsonPath("$.member_count").value(1))
            .andExpect(jsonPath("$.members").isArray())
            .andExpect(jsonPath("$.package").doesNotExist())
            .andExpect(jsonPath("$.transfer_pending").doesNotExist())
            .andExpect(jsonPath("$.name").doesNotExist());
        verify(groupDetailsService).toDetailResponse(g);
    }

    @Test void getGroupForbiddenForNonMember() throws Exception {
        FamilyGroupEntity g = grp(5L, 1L);
        AuthContext.set(new AuthContext.UserContext(9L, "SUBSCRIBER"));
        when(groupRepository.findByIdAndStatus(5L, GroupStatus.ACTIVE)).thenReturn(Optional.of(g));
        when(memberRepository.findByGroupIdAndUserIdAndStatus(5L, 9L, MemberStatus.ACTIVE)).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/v1/groups/5"))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
    }

    @Test void getGroupAllowedForAdmin() throws Exception {
        FamilyGroupEntity g = grp(5L, 1L);
        AuthContext.set(new AuthContext.UserContext(9L, "ADMIN"));
        when(groupRepository.findByIdAndStatus(5L, GroupStatus.ACTIVE)).thenReturn(Optional.of(g));
        when(groupDetailsService.toDetailResponse(g)).thenReturn(details(5L, 1L));

        mockMvc.perform(get("/api/v1/groups/5"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.group_id").value(5));
    }

    @Test void getGroupReturns404WhenGroupNotFound() throws Exception {
        when(groupRepository.findByIdAndStatus(99L, GroupStatus.ACTIVE)).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/v1/groups/99"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.error.code").value("GROUP_NOT_FOUND"));
    }

    @Test void createGroupReturns201AndDelegatesToService() throws Exception {
        FamilyGroupEntity saved = grp(42L, 1L);
        when(familyGroupService.createGroup(eq(1L), any())).thenReturn(saved);
        when(groupDetailsService.toDetailResponse(saved)).thenReturn(details(42L, 1L));

        mockMvc.perform(post("/api/v1/groups")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(new FamilyGroupCreateRequest(1L, List.of(2L, 3L)))))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.group_id").value(42))
            .andExpect(jsonPath("$.owner_id").value(1))
            .andExpect(jsonPath("$.status").value("ACTIVE"))
            .andExpect(jsonPath("$.member_count").value(1))
            .andExpect(jsonPath("$.members").isArray());
        verify(groupDetailsService).toDetailResponse(saved);
    }

    @Test void addMemberReturns201() throws Exception {
        mockMvc.perform(post("/api/v1/groups/5/members")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(new AddMemberRequest(7L))))
            .andExpect(status().isCreated());
        verify(familyGroupService).addMember(5L, 7L, null);
    }

    @Test void addMemberUsesCallerIdFromAuthContext() throws Exception {
        AuthContext.set(new AuthContext.UserContext(3L, "OWNER"));

        mockMvc.perform(post("/api/v1/groups/5/members")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(new AddMemberRequest(7L))))
            .andExpect(status().isCreated());

        verify(familyGroupService).addMember(5L, 7L, 3L);
    }

    @Test void addMemberReturns409WhenServiceThrowsConflict() throws Exception {
        doThrow(new BusinessRuleViolationException("BR_01_USER_ALREADY_IN_GROUP", "dup", HttpStatus.CONFLICT))
            .when(familyGroupService).addMember(eq(5L), eq(7L), any());

        mockMvc.perform(post("/api/v1/groups/5/members")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(new AddMemberRequest(7L))))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.error.code").value("BR_01_USER_ALREADY_IN_GROUP"));
    }

    @Test void removeMemberReturns204() throws Exception {
        mockMvc.perform(delete("/api/v1/groups/5/members/7"))
            .andExpect(status().isNoContent());
        verify(familyGroupService).removeMember(5L, 7L, null);
    }

    @Test void leaveGroupReturns204() throws Exception {
        mockMvc.perform(post("/api/v1/groups/5/leave")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(new LeaveGroupRequest(7L))))
            .andExpect(status().isNoContent());
        verify(familyGroupService).leaveGroup(5L, 7L, null);
    }

    @Test void leaveGroupRejectsMissingUserId() throws Exception {
        mockMvc.perform(post("/api/v1/groups/5/leave")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isBadRequest());
    }

    @Test void transferOwnershipReturns204() throws Exception {
        mockMvc.perform(post("/api/v1/groups/5/transfer-ownership")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(new TransferOwnershipRequest(2L))))
            .andExpect(status().isNoContent());
        verify(familyGroupService).transferOwnership(5L, 2L, null);
    }

    @Test void disbandGroupReturns204() throws Exception {
        mockMvc.perform(post("/api/v1/groups/5/disband"))
            .andExpect(status().isNoContent());
        verify(familyGroupService).disbandGroup(5L, null);
    }

    @Test void getMyGroupReturnsActiveGroupDetails() throws Exception {
        AuthContext.set(new AuthContext.UserContext(1L, "OWNER"));
        GroupMemberEntity m = membership(10L, 1L);
        FamilyGroupEntity group = grp(10L, 1L);
        when(memberRepository.findByUserIdAndStatus(1L, MemberStatus.ACTIVE))
            .thenReturn(Optional.of(m));
        when(groupRepository.findByIdAndStatus(10L, GroupStatus.ACTIVE)).thenReturn(Optional.of(group));
        when(groupDetailsService.toDetailResponse(group)).thenReturn(details(10L, 1L));

        mockMvc.perform(get("/api/v1/groups/me"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.group_id").value(10))
            .andExpect(jsonPath("$.owner_id").value(1))
            .andExpect(jsonPath("$.status").value("ACTIVE"))
            .andExpect(jsonPath("$.created_at").value("2026-01-01T00:00:00Z"))
            .andExpect(jsonPath("$.member_count").value(1))
            .andExpect(jsonPath("$.members").isArray())
            .andExpect(jsonPath("$.package").doesNotExist())
            .andExpect(jsonPath("$.transfer_pending").doesNotExist())
            .andExpect(jsonPath("$.role").doesNotExist());
        verify(groupDetailsService).toDetailResponse(group);
    }

    @Test void getMyGroupReturns404WhenNoActiveGroup() throws Exception {
        AuthContext.set(new AuthContext.UserContext(1L, "SUBSCRIBER"));
        when(memberRepository.findByUserIdAndStatus(1L, MemberStatus.ACTIVE))
            .thenReturn(Optional.empty());

        mockMvc.perform(get("/api/v1/groups/me"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.error.code").value("GROUP_NOT_FOUND"));
    }

    @Test void getMyGroupReturns404WhenGroupIsNotActive() throws Exception {
        AuthContext.set(new AuthContext.UserContext(1L, "OWNER"));
        GroupMemberEntity m = membership(10L, 1L);
        when(memberRepository.findByUserIdAndStatus(1L, MemberStatus.ACTIVE)).thenReturn(Optional.of(m));
        when(groupRepository.findByIdAndStatus(10L, GroupStatus.ACTIVE)).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/v1/groups/me"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.error.code").value("GROUP_NOT_FOUND"));
    }

    @Test void getMyGroupReturns401WithoutAuth() throws Exception {
        mockMvc.perform(get("/api/v1/groups/me"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.error.code").value("UNAUTHENTICATED"));
    }

    @Test void getContactsReturnsUsersWithoutActiveGroup() throws Exception {
        AuthContext.set(new AuthContext.UserContext(1L, "OWNER"));
        UserEntity freeUser = user(2L, "+79990000002", "Пётр");
        UserEntity busyUser = user(3L, "+79990000003", "Иван");
        when(userRepository.findAll()).thenReturn(List.of(freeUser, busyUser));
        when(memberRepository.findUserIdsByStatus(MemberStatus.ACTIVE))
            .thenReturn(List.of(1L, 3L)); // 1L — caller, 3L — в группе

        mockMvc.perform(get("/api/v1/groups/contacts"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.contacts.length()").value(1))
            .andExpect(jsonPath("$.contacts[0].user_id").value(2))
            .andExpect(jsonPath("$.contacts[0].phone_number").value("+79990000002"))
            .andExpect(jsonPath("$.contacts[0].name").value("Пётр"));
    }

    @Test void getContactsReturnsEmptyListWhenAllUsersInGroups() throws Exception {
        AuthContext.set(new AuthContext.UserContext(1L, "OWNER"));
        when(userRepository.findAll()).thenReturn(List.of(user(2L, "+79990000002", "Пётр")));
        when(memberRepository.findUserIdsByStatus(MemberStatus.ACTIVE))
            .thenReturn(List.of(1L, 2L));

        mockMvc.perform(get("/api/v1/groups/contacts"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.contacts.length()").value(0));
    }

    @Test void getContactsReturns401WithoutAuth() throws Exception {
        mockMvc.perform(get("/api/v1/groups/contacts"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.error.code").value("UNAUTHENTICATED"));
    }

    @Test void setMemberLimitReturnsResponseAndDelegatesToUserLimitService() throws Exception {
        AuthContext.set(new AuthContext.UserContext(1L, "OWNER"));
        UserLimitResponse response = new UserLimitResponse(
            2L, 5L, "TRAFFIC", 100.0, 10.0, "ACTIVE", 50.0, 1000.0);
        when(userLimitService.setMemberLimit(5L, 2L, "TRAFFIC", 100.0, 1L)).thenReturn(response);

        mockMvc.perform(put("/api/v1/groups/5/members/2/limits")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(new SetLimitRequest("TRAFFIC", 100.0))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.user_id").value(2))
            .andExpect(jsonPath("$.group_id").value(5))
            .andExpect(jsonPath("$.resource_type").value("TRAFFIC"))
            .andExpect(jsonPath("$.limit_value").value(100.0))
            .andExpect(jsonPath("$.used_value").value(10.0))
            .andExpect(jsonPath("$.status").value("ACTIVE"))
            .andExpect(jsonPath("$.min_allowed").value(50.0))
            .andExpect(jsonPath("$.max_allowed").value(1000.0));
        verify(userLimitService).setMemberLimit(5L, 2L, "TRAFFIC", 100.0, 1L);
    }

    @Test void setMemberLimitRejectsMissingResourceType() throws Exception {
        mockMvc.perform(put("/api/v1/groups/5/members/2/limits")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"limit_value\":100.0}"))
            .andExpect(status().isBadRequest());
        verify(userLimitService, never()).setMemberLimit(any(), any(), any(), any(), any());
    }
}
