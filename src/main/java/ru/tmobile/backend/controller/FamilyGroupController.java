package ru.tmobile.backend.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import ru.tmobile.backend.dto.familyGroup.AddMemberRequest;
import ru.tmobile.backend.dto.familyGroup.Contact;
import ru.tmobile.backend.dto.familyGroup.ContactsResponse;
import ru.tmobile.backend.dto.familyGroup.FamilyGroupCreateRequest;
import ru.tmobile.backend.dto.familyGroup.FamilyGroupResponse;
import ru.tmobile.backend.dto.familyGroup.GroupDetailResponse;
import ru.tmobile.backend.dto.familyGroup.GroupLimitResponse;
import ru.tmobile.backend.dto.familyGroup.SetLimitRequest;
import ru.tmobile.backend.dto.familyGroup.TransferOwnershipRequest;
import ru.tmobile.backend.dto.familyGroup.TransferPendingResponse;
import ru.tmobile.backend.dto.familyGroup.UserLimitResponse;
import ru.tmobile.backend.entity.FamilyGroupEntity;
import ru.tmobile.backend.entity.FamilyGroupEntity.GroupStatus;
import ru.tmobile.backend.entity.GroupMemberEntity;
import ru.tmobile.backend.entity.GroupMemberEntity.MemberStatus;
import ru.tmobile.backend.exception.AuthenticationException;
import ru.tmobile.backend.exception.AuthorizationException;
import ru.tmobile.backend.exception.BusinessRuleViolationException;
import ru.tmobile.backend.repository.FamilyGroupRepository;
import ru.tmobile.backend.repository.GroupMemberRepository;
import ru.tmobile.backend.repository.UserRepository;
import ru.tmobile.backend.security.AuthContext;
import ru.tmobile.backend.service.FamilyGroupService;
import ru.tmobile.backend.service.GroupDetailsService;
import ru.tmobile.backend.service.UserLimitService;



@RestController
@RequestMapping("/api/v1/groups")
@RequiredArgsConstructor
public class FamilyGroupController {

    private final FamilyGroupService familyGroupService;
    private final GroupDetailsService groupDetailsService;
    private final FamilyGroupRepository groupRepository;
    private final GroupMemberRepository memberRepository;
    private final UserRepository userRepository;
    private final UserLimitService userLimitService;

    @GetMapping
    public List<FamilyGroupResponse> getGroups() {
        return groupRepository.findAll().stream()
            .map(this::toResponse)
            .toList();
    }

    @GetMapping("/{groupId}")
    public GroupDetailResponse getGroup(@PathVariable Long groupId) {
        FamilyGroupEntity group = groupRepository.findByIdAndStatus(groupId, GroupStatus.ACTIVE)
            .orElseThrow(() -> new BusinessRuleViolationException(
                "GROUP_NOT_FOUND", "Group not found: " + groupId, HttpStatus.NOT_FOUND));

        Long callerId = AuthContext.getUserId();
        boolean isAdmin = "ADMIN".equals(AuthContext.getRole());
        boolean isMember = callerId != null && memberRepository
            .findByGroupIdAndUserIdAndStatus(groupId, callerId, MemberStatus.ACTIVE).isPresent();

        if (!isAdmin && !isMember) {
            throw new AuthorizationException("FORBIDDEN", "User is not a member of the group");
        }

        return groupDetailsService.toDetailResponse(group);
    }

    @PostMapping
    public ResponseEntity<GroupDetailResponse> createGroup(@Valid @RequestBody FamilyGroupCreateRequest request) {
        FamilyGroupEntity group = familyGroupService.createGroup(request.ownerId(), request.memberIds());
        return ResponseEntity.status(HttpStatus.CREATED).body(groupDetailsService.toDetailResponse(group));
    }

    @PostMapping("/{groupId}/members")
    public ResponseEntity<Void> addMember(@PathVariable Long groupId, @Valid @RequestBody AddMemberRequest request) {
        Long callerId = AuthContext.getUserId();
        familyGroupService.addMember(groupId, request.userId(), callerId);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    @PostMapping("/{groupId}/members/{userId}/exclude")
    public ResponseEntity<Void> excludeMember(@PathVariable Long groupId, @PathVariable Long userId) {
        Long callerId = AuthContext.getUserId();
        familyGroupService.excludeMember(groupId, userId, callerId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{groupId}/leave")
    public ResponseEntity<Void> leaveGroup(@PathVariable Long groupId) {
        Long callerId = AuthContext.getUserId();
        familyGroupService.leaveGroup(groupId, callerId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{groupId}/transfer")
    public ResponseEntity<TransferPendingResponse> initiateTransfer(@PathVariable Long groupId, @Valid @RequestBody TransferOwnershipRequest request) {
        Long callerId = AuthContext.getUserId();
        TransferPendingResponse response = familyGroupService.initiateTransfer(groupId, request.newOwnerId(), callerId);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{groupId}/transfer/accept")
    public ResponseEntity<GroupDetailResponse> acceptTransfer(@PathVariable Long groupId) {
        Long callerId = AuthContext.getUserId();
        FamilyGroupEntity group = familyGroupService.acceptTransfer(groupId, callerId);
        return ResponseEntity.ok(groupDetailsService.toDetailResponse(group));
    }
    

    @PostMapping("/{groupId}/disband")
    public ResponseEntity<Void> disbandGroup(@PathVariable Long groupId) {
        Long callerId = AuthContext.getUserId();
        familyGroupService.disbandGroup(groupId, callerId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/me")
    public GroupDetailResponse getMyGroup() {
        Long callerId = AuthContext.getUserId();
        if (callerId == null) {
            throw new AuthenticationException("UNAUTHENTICATED", "User not authenticated");
        }
        GroupMemberEntity membership = memberRepository
            .findByUserIdAndStatus(callerId, MemberStatus.ACTIVE)
            .orElseThrow(() -> new BusinessRuleViolationException(
                "GROUP_NOT_FOUND", "User has no active group", HttpStatus.NOT_FOUND));

        FamilyGroupEntity group = groupRepository.findByIdAndStatus(membership.getGroupId(), GroupStatus.ACTIVE)
            .orElseThrow(() -> new BusinessRuleViolationException(
                "GROUP_NOT_FOUND", "User has no active group", HttpStatus.NOT_FOUND));

        return groupDetailsService.toDetailResponse(group);
    }

    @GetMapping("/contacts")
    public ContactsResponse getContacts() {
        Long callerId = AuthContext.getUserId();
        if (callerId == null) {
            throw new AuthenticationException("UNAUTHENTICATED", "User not authenticated");
        }

        List<Long> busyUserIds = memberRepository.findUserIdsByStatus(MemberStatus.ACTIVE);
        List<Contact> contacts = userRepository.findAll().stream()
            .filter(u -> !busyUserIds.contains(u.getId()))
            .map(u -> new Contact(u.getId(), u.getPhoneNumber(), u.getName()))
            .toList();

        return new ContactsResponse(contacts);
    }

    @PutMapping("/{groupId}/members/{userId}/limits")
    public UserLimitResponse setMemberLimit(
            @PathVariable Long groupId,
            @PathVariable Long userId,
            @Valid @RequestBody SetLimitRequest request) {
        Long callerId = AuthContext.getUserId();
        return userLimitService.setMemberLimit(
            groupId, userId, request.resourceType(), request.limitValue(), callerId);
    }

    @GetMapping("/{groupId}/limits")
    public GroupLimitResponse getGroupLimit(@PathVariable Long groupId) {
        FamilyGroupEntity group = groupRepository.findByIdAndStatus(groupId, GroupStatus.ACTIVE)
            .orElseThrow(() -> new BusinessRuleViolationException(
                "GROUP_NOT_FOUND", "Group not found " + groupId, HttpStatus.NOT_FOUND));
        Long callerId = AuthContext.getUserId();
        if (callerId == null) {
            throw new AuthenticationException("UNAUTHENTICATED", "User nit authenticated");
        }
        return groupDetailsService.toLimitResponse(group, callerId);
    }

    private FamilyGroupResponse toResponse(FamilyGroupEntity group) {
        int membersCount = memberRepository.findByGroupIdAndStatus(group.getId(), MemberStatus.ACTIVE).size();
        return new FamilyGroupResponse(
            group.getId(),
            group.getOwnerId(),
            group.getStatus(),
            group.getCreatedAt(),
            membersCount
        );
    }
}
