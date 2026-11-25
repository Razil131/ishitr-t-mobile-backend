package ru.tmobile.backend.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
import ru.tmobile.backend.dto.familyGroup.LeaveGroupRequest;
import ru.tmobile.backend.dto.familyGroup.TransferOwnershipRequest;
import ru.tmobile.backend.entity.FamilyGroupEntity;
import ru.tmobile.backend.entity.FamilyGroupEntity.GroupStatus;
import ru.tmobile.backend.entity.GroupMemberEntity;
import ru.tmobile.backend.entity.GroupMemberEntity.MemberStatus;
import ru.tmobile.backend.exception.BusinessRuleViolationException;
import ru.tmobile.backend.exception.GlobalExceptionHandler;
import ru.tmobile.backend.repository.FamilyGroupRepository;
import ru.tmobile.backend.repository.GroupMemberRepository;
import ru.tmobile.backend.security.AuthContext;
import ru.tmobile.backend.service.FamilyGroupService;

class FamilyGroupControllerTest {
    MockMvc mockMvc;
    FamilyGroupService familyGroupService;
    FamilyGroupRepository groupRepository;
    GroupMemberRepository memberRepository;
    ObjectMapper json;

    @BeforeEach void up() {
        familyGroupService = mock(FamilyGroupService.class);
        groupRepository = mock(FamilyGroupRepository.class);
        memberRepository = mock(GroupMemberRepository.class);
        mockMvc = MockMvcBuilders.standaloneSetup(
                new FamilyGroupController(familyGroupService, groupRepository, memberRepository))
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
        g.setCreatedAt(OffsetDateTime.of(2026,1,1,0,0,0,0,ZoneOffset.UTC));
        return g;
    }
    private GroupMemberEntity membership(long gid, long uid) {
        GroupMemberEntity m = new GroupMemberEntity();
        m.setGroupId(gid); m.setUserId(uid); m.setStatus(MemberStatus.ACTIVE);
        return m;
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
        when(memberRepository.findByGroupIdAndStatus(5L, MemberStatus.ACTIVE)).thenReturn(List.of(membership(5L, 1L)));

        mockMvc.perform(get("/api/v1/groups/5"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.group_id").value(5))
            .andExpect(jsonPath("$.members_count").value(1))
            .andExpect(jsonPath("$.owner_name").doesNotExist())
            .andExpect(jsonPath("$.name").doesNotExist());
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
        when(memberRepository.findByGroupIdAndStatus(5L, MemberStatus.ACTIVE)).thenReturn(List.of());

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
        when(memberRepository.findByGroupIdAndStatus(42L, MemberStatus.ACTIVE)).thenReturn(List.of());

        mockMvc.perform(post("/api/v1/groups")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(new FamilyGroupCreateRequest(1L, List.of(2L, 3L)))))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.group_id").value(42));
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
}
