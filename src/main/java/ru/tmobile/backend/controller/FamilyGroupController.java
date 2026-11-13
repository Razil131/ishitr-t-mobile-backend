package ru.tmobile.backend.controller;

import lombok.RequiredArgsConstructor;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import ru.tmobile.backend.dto.AddMemberRequest;
import ru.tmobile.backend.dto.FamilyGroupCreateRequest;
import ru.tmobile.backend.dto.LeaveGroupRequest;
import ru.tmobile.backend.dto.TransferOwnershipRequest;
import ru.tmobile.backend.entity.FamilyGroupEntity;
import ru.tmobile.backend.entity.GroupMemberEntity;
import ru.tmobile.backend.entity.UserEntity;
import ru.tmobile.backend.entity.GroupMemberEntity.MemberStatus;
import ru.tmobile.backend.repository.FamilyGroupRepository;
import ru.tmobile.backend.repository.GroupMemberRepository;
import ru.tmobile.backend.repository.UserRepository;
import ru.tmobile.backend.service.FamilyGroupService;

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
    @PostMapping
    public ResponseEntity<FamilyGroupEntity> createGroup(@Valid @RequestBody FamilyGroupCreateRequest request) {
        FamilyGroupEntity group = familyGroupService.createGroup(
            request.getOwnerId(), 
            request.getMemberIds()
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(group);
    }

    @PostMapping("/{groupId}/members")
    public ResponseEntity<Void> addMember(@PathVariable Long groupId, @Valid @RequestBody AddMemberRequest request) {
        familyGroupService.addMember(groupId, request.getUserId());
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    @DeleteMapping("/{groupId}/members/{userId}")
    public ResponseEntity<Void> removeMember(@PathVariable Long groupId, @PathVariable Long userId) {
        familyGroupService.removeMember(groupId, userId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{groupId}/leave")
    public ResponseEntity<Void> leaveGroup(@PathVariable Long groupId, @RequestBody LeaveGroupRequest request) {
        familyGroupService.leaveGroup(groupId, request.getUserId());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{groupId}/transfer-ownership")
    public ResponseEntity<Void> transferOwnership(@PathVariable Long groupId, @Valid @RequestBody TransferOwnershipRequest request) {
        familyGroupService.transferOwnership(groupId, request.getNewOwnerId());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{groupId}/disband")
    public ResponseEntity<Void> disbandGroup(@PathVariable Long groupId) {
        familyGroupService.disbandGroup(groupId);
        return ResponseEntity.noContent().build();
    }

}
