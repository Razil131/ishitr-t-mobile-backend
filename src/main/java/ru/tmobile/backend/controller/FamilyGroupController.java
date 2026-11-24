package ru.tmobile.backend.controller;

import lombok.RequiredArgsConstructor;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import ru.tmobile.backend.dto.familyGroup.AddMemberRequest;
import ru.tmobile.backend.dto.familyGroup.FamilyGroupCreateRequest;
import ru.tmobile.backend.dto.familyGroup.LeaveGroupRequest;
import ru.tmobile.backend.dto.familyGroup.TransferOwnershipRequest;
import ru.tmobile.backend.entity.FamilyGroupEntity;
import ru.tmobile.backend.entity.GroupMemberEntity;
import ru.tmobile.backend.entity.UserEntity;
import ru.tmobile.backend.exception.BusinessRuleViolationException;
import ru.tmobile.backend.entity.FamilyGroupEntity.GroupStatus;
import ru.tmobile.backend.entity.GroupMemberEntity.MemberStatus;
import ru.tmobile.backend.repository.FamilyGroupRepository;
import ru.tmobile.backend.repository.GroupMemberRepository;
import ru.tmobile.backend.repository.UserRepository;
import ru.tmobile.backend.service.FamilyGroupService;
import ru.tmobile.backend.security.AuthContext;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/groups")
@RequiredArgsConstructor
public class FamilyGroupController {

    private final FamilyGroupService familyGroupService;
    private final FamilyGroupRepository groupRepository;
    private final GroupMemberRepository memberRepository;
    private final UserRepository userRepository;

    @GetMapping
    public List<Map<String, Object>> getGroups() {
        List<FamilyGroupEntity> groups = groupRepository.findAll();

        return groups.stream().map(g -> {
            List<GroupMemberEntity> members = memberRepository.findByGroupIdAndStatus(g.getId(), MemberStatus.ACTIVE);
            Optional<UserEntity> owner = userRepository.findById(g.getOwnerId());

            return Map.<String, Object>of(
                    "group_id", g.getId(),
                    "owner_id", g.getOwnerId(),
                    "owner_name", owner.map(UserEntity::getName).orElse(""),
                    "status", g.getStatus().name(),
                    "created_at", g.getCreatedAt().toString(),
                    "members_count", members.size(),
                    "name", owner.map(o -> "Семья " + o.getName().split(" ")[0]).orElse("Группа #" + g.getId())
            );
        }).collect(Collectors.toList());
    }

    @GetMapping("/{groupId}")
    public Map<String, Object> getGroup(@PathVariable Long groupId) {
        FamilyGroupEntity group = groupRepository.findByIdAndStatus(groupId, GroupStatus.ACTIVE)
            .orElseThrow(() -> new BusinessRuleViolationException("GROUP_NOT_FOUND", "Group not found: " + groupId, HttpStatus.NOT_FOUND));

        List<GroupMemberEntity> members = memberRepository.findByGroupIdAndStatus(group.getId(), MemberStatus.ACTIVE);
        Optional<UserEntity> owner = userRepository.findById(group.getOwnerId());
        return Map.<String, Object>of(
                "group_id", group.getId(),
                "owner_id", group.getOwnerId(),
                "owner_name", owner.map(UserEntity::getName).orElse(""),
                "status", group.getStatus().name(),
                "created_at", group.getCreatedAt().toString(),
                "members_count", members.size(),
                "name", owner.map(o -> "Семья " + o.getName().split(" ")[0]).orElse("Группа #" + group.getId())
        );
    }

    @PostMapping
    public ResponseEntity<FamilyGroupEntity> createGroup(@Valid @RequestBody FamilyGroupCreateRequest request) {
        FamilyGroupEntity group = familyGroupService.createGroup(
            request.ownerId(), 
            request.memberIds()
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(group);
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
    public ResponseEntity<Void> leaveGroup(@PathVariable Long groupId, @RequestBody LeaveGroupRequest request) {
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

}
