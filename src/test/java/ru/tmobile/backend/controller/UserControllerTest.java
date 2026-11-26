package ru.tmobile.backend.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.fasterxml.jackson.databind.ObjectMapper;

import ru.tmobile.backend.dto.familyGroup.GroupContext;
import ru.tmobile.backend.dto.auth.UpdateUserRequest;
import ru.tmobile.backend.dto.auth.UserProfile;
import ru.tmobile.backend.dto.auth.UserProfileResponse;
import ru.tmobile.backend.entity.FamilyGroupEntity;
import ru.tmobile.backend.entity.FamilyGroupEntity.GroupStatus;
import ru.tmobile.backend.entity.GroupMemberEntity;
import ru.tmobile.backend.entity.GroupMemberEntity.MemberRole;
import ru.tmobile.backend.entity.GroupMemberEntity.MemberStatus;
import ru.tmobile.backend.entity.UserEntity;
import ru.tmobile.backend.exception.GlobalExceptionHandler;
import ru.tmobile.backend.repository.FamilyGroupRepository;
import ru.tmobile.backend.repository.GroupMemberRepository;
import ru.tmobile.backend.repository.UserRepository;
import ru.tmobile.backend.security.AuthContext;
import ru.tmobile.backend.service.UserService;

class UserControllerTest {
    MockMvc mockMvc;
    UserRepository userRepository;
    GroupMemberRepository groupMemberRepository;
    FamilyGroupRepository familyGroupRepository;
    UserService userService;
    ObjectMapper json = new ObjectMapper();

    @BeforeEach void up() {
        userRepository = mock(UserRepository.class);
        groupMemberRepository = mock(GroupMemberRepository.class);
        familyGroupRepository = mock(FamilyGroupRepository.class);
        userService = mock(UserService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(
            new UserController(userRepository, groupMemberRepository, familyGroupRepository, userService))
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();
    }

    @AfterEach void tearDown() {
        AuthContext.clear();
    }

    private UserEntity user(long id, String phone, String name) {
        UserEntity u = new UserEntity();
        u.setId(id); u.setPhoneNumber(phone); u.setName(name);
        u.setCreatedAt(OffsetDateTime.now(ZoneOffset.UTC));
        return u;
    }
    private GroupMemberEntity member(long gid, long uid, MemberRole role) {
        GroupMemberEntity m = new GroupMemberEntity();
        m.setId(1L); m.setGroupId(gid); m.setUserId(uid);
        m.setRole(role); m.setStatus(MemberStatus.ACTIVE);
        m.setJoinedAt(OffsetDateTime.now(ZoneOffset.UTC));
        return m;
    }

    @Test void activeUsersReturnsListFromRepository() throws Exception {
        when(groupMemberRepository.findUserIdsByStatus(MemberStatus.ACTIVE))
            .thenReturn(List.of(1L, 2L, 3L));

        mockMvc.perform(get("/api/v1/users/active"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0]").value(1))
            .andExpect(jsonPath("$[1]").value(2))
            .andExpect(jsonPath("$[2]").value(3));
    }

    @Test void activeUsersReturnsEmptyArrayWhenNoneActive() throws Exception {
        when(groupMemberRepository.findUserIdsByStatus(MemberStatus.ACTIVE)).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/users/active"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(0));
    }

    @Test void getUsersEnrichesUsersWithGroupAndRole() throws Exception {
        UserEntity u1 = user(1L, "+79990000001", "Иван Иванов");
        UserEntity u2 = user(2L, "+79990000002", "Пётр Петров");
        GroupMemberEntity m1 = member(10L, 1L, MemberRole.OWNER);
        FamilyGroupEntity g = new FamilyGroupEntity();
        g.setId(10L); g.setOwnerId(1L); g.setStatus(GroupStatus.ACTIVE);
        g.setCreatedAt(OffsetDateTime.now(ZoneOffset.UTC));

        when(userRepository.findAll()).thenReturn(List.of(u1, u2));
        when(groupMemberRepository.findAll()).thenReturn(List.of(m1));
        when(familyGroupRepository.findAll()).thenReturn(List.of(g));
        when(userRepository.findById(1L)).thenReturn(java.util.Optional.of(u1));

        mockMvc.perform(get("/api/v1/users"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].user_id").value(1))
            .andExpect(jsonPath("$[0].name").value("Иван Иванов"))
            .andExpect(jsonPath("$[0].group_id").value(10))
            .andExpect(jsonPath("$[0].group_name").value("Семья Иван"))
            .andExpect(jsonPath("$[0].role").value("OWNER"))
            .andExpect(jsonPath("$[1].group_id").value(0))
            .andExpect(jsonPath("$[1].group_name").value(""))
            .andExpect(jsonPath("$[1].role").value("MEMBER"));
    }

    @Test void getUsersUsesEmptyNameWhenUserHasNoName() throws Exception {
        UserEntity u = user(1L, "+79990000001", null);
        when(userRepository.findAll()).thenReturn(List.of(u));
        when(groupMemberRepository.findAll()).thenReturn(List.of());
        when(familyGroupRepository.findAll()).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/users"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].name").value(""));
    }

    @Test void getProfileReturnsCurrentUserProfile() throws Exception {
        AuthContext.set(new AuthContext.UserContext(1L, "OWNER"));
        UserProfile profile = new UserProfile(1L, "+79990000001", "Иван", OffsetDateTime.now(ZoneOffset.UTC));
        GroupContext groupContext = new GroupContext(10L, "OWNER", 2);
        when(userService.getProfile(1L)).thenReturn(new UserProfileResponse(profile, groupContext));

        mockMvc.perform(get("/api/v1/users/me"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.user.user_id").value(1))
            .andExpect(jsonPath("$.user.name").value("Иван"))
            .andExpect(jsonPath("$.group.group_id").value(10))
            .andExpect(jsonPath("$.group.role").value("OWNER"))
            .andExpect(jsonPath("$.group.member_count").value(2));
    }

    @Test void getProfileReturns401WhenNotAuthenticated() throws Exception {
        mockMvc.perform(get("/api/v1/users/me"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.error.code").value("UNAUTHENTICATED"));
    }

    @Test void updateProfileUpdatesNameAndReturnsProfile() throws Exception {
        AuthContext.set(new AuthContext.UserContext(1L, "MEMBER"));
        UserProfile profile = new UserProfile(1L, "+79990000001", "Новое Имя", OffsetDateTime.now(ZoneOffset.UTC));
        when(userService.updateProfile(eq(1L), any(UpdateUserRequest.class)))
            .thenReturn(new UserProfileResponse(profile, null));

        mockMvc.perform(patch("/api/v1/users/me")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(new UpdateUserRequest("Новое Имя"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.user.name").value("Новое Имя"));
    }

    @Test void updateProfileReturns401WhenNotAuthenticated() throws Exception {
        mockMvc.perform(patch("/api/v1/users/me")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"test\"}"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.error.code").value("UNAUTHENTICATED"));
    }
}
