package ru.tmobile.backend.controller;

import lombok.RequiredArgsConstructor;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import ru.tmobile.backend.dto.familyGroup.AddMemberRequest;
import ru.tmobile.backend.dto.familyGroup.FamilyGroupCreateRequest;
import ru.tmobile.backend.dto.familyGroup.FamilyGroupResponse;
import ru.tmobile.backend.dto.familyGroup.LeaveGroupRequest;
import ru.tmobile.backend.dto.familyGroup.TransferOwnershipRequest;
import ru.tmobile.backend.entity.FamilyGroupEntity;
import ru.tmobile.backend.exception.AuthorizationException;
import ru.tmobile.backend.exception.BusinessRuleViolationException;
import ru.tmobile.backend.entity.FamilyGroupEntity.GroupStatus;
import ru.tmobile.backend.entity.GroupMemberEntity.MemberStatus;
import ru.tmobile.backend.repository.FamilyGroupRepository;
import ru.tmobile.backend.repository.GroupMemberRepository;
import ru.tmobile.backend.service.FamilyGroupService;
import ru.tmobile.backend.security.AuthContext;

import java.util.List;

@RestController
@RequestMapping("/api/v1/groups")
@RequiredArgsConstructor
public class FamilyGroupController {

    private final FamilyGroupService familyGroupService;
    private final FamilyGroupRepository groupRepository;
    private final GroupMemberRepository memberRepository;

    @GetMapping
    public List<FamilyGroupResponse> getGroups() {
        return groupRepository.findAll().stream()
            .map(this::toResponse)
            .toList();
    }

    @GetMapping("/{groupId}")
    public FamilyGroupResponse getGroup(@PathVariable Long groupId) {
        FamilyGroupEntity group = groupRepository.findByIdAndStatus(groupId, GroupStatus.ACTIVE)
            .orElseThrow(() -> new BusinessRuleViolationException("GROUP_NOT_FOUND", "Group not found: " + groupId, HttpStatus.NOT_FOUND));

        Long callerId = AuthContext.getUserId();
        boolean isAdmin = "ADMIN".equals(AuthContext.getRole());
        boolean isMember = callerId != null && memberRepository
            .findByGroupIdAndUserIdAndStatus(groupId, callerId, MemberStatus.ACTIVE).isPresent();

        if (!isAdmin && !isMember) {
            throw new AuthorizationException("FORBIDDEN", "User is not a member of the group");
        }

        return toResponse(group);
    }

    @PostMapping
    public ResponseEntity<FamilyGroupResponse> createGroup(@Valid @RequestBody FamilyGroupCreateRequest request) {
        FamilyGroupEntity group = familyGroupService.createGroup(
            request.ownerId(), 
            request.memberIds()
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(group));
    }

    @PostMapping("/{groupId}/members")
    public ResponseEntity<Void> addMember(@PathVariable Long groupId, @Valid @RequestBody AddMemberRequest request) {
        Long callerId = AuthContext.getUserId();
        familyGroupService.addMember(groupId, request.userId(), callerId);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    @DeleteMapping("/{groupId}/members/{userId}")
    public ResponseEntity<Void> removeMember(@PathVariable Long groupId, @PathVariable Long userId) {
        Long callerId = AuthContext.getUserId();
        familyGroupService.removeMember(groupId, userId, callerId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{groupId}/leave")
    public ResponseEntity<Void> leaveGroup(@PathVariable Long groupId, @Valid @RequestBody LeaveGroupRequest request) {
        Long callerId = AuthContext.getUserId();
        familyGroupService.leaveGroup(groupId, request.userId(), callerId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{groupId}/transfer-ownership")
    public ResponseEntity<Void> transferOwnership(@PathVariable Long groupId, @Valid @RequestBody TransferOwnershipRequest request) {
        Long callerId = AuthContext.getUserId();
        familyGroupService.transferOwnership(groupId, request.newOwnerId(), callerId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{groupId}/disband")
    public ResponseEntity<Void> disbandGroup(@PathVariable Long groupId) {
        Long callerId = AuthContext.getUserId();
        familyGroupService.disbandGroup(groupId, callerId);
        return ResponseEntity.noContent().build();
    }

    private FamilyGroupResponse toResponse(FamilyGroupEntity group) {
        int membersCount = memberRepository.findByGroupIdAndStatus(group.getId(),MemberStatus.ACTIVE).size();
        return new FamilyGroupResponse(
            group.getId(),
            group.getOwnerId(),
            group.getStatus(),
            group.getCreatedAt(),
            membersCount
        );
    }
}
