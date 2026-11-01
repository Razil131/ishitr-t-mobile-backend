package ru.tmobile.backend.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import ru.tmobile.backend.entity.FamilyGroupEntity;
import ru.tmobile.backend.entity.GroupMemberEntity;
import ru.tmobile.backend.entity.UserEntity;
import ru.tmobile.backend.repository.FamilyGroupRepository;
import ru.tmobile.backend.repository.GroupMemberRepository;
import ru.tmobile.backend.repository.UserRepository;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {

    private final UserRepository userRepository;
    private final GroupMemberRepository groupMemberRepository;
    private final FamilyGroupRepository familyGroupRepository;

    @GetMapping("/active")
    public List<Long> getActiveUsers() {
        return groupMemberRepository.findByStatus("ACTIVE")
                .stream()
                .map(GroupMemberEntity::getUserId)
                .collect(Collectors.toList());
    }

    @GetMapping
    public List<Map<String, Object>> getUsers() {
        List<UserEntity> users = userRepository.findAll();
        List<GroupMemberEntity> members = groupMemberRepository.findAll();
        List<FamilyGroupEntity> groups = familyGroupRepository.findAll();

        Map<Long, GroupMemberEntity> memberMap = members.stream()
                .collect(Collectors.toMap(GroupMemberEntity::getUserId, m -> m, (a, b) -> a));
        Map<Long, FamilyGroupEntity> groupMap = groups.stream()
                .collect(Collectors.toMap(FamilyGroupEntity::getId, g -> g, (a, b) -> a));

        return users.stream().map(u -> {
            GroupMemberEntity member = memberMap.get(u.getId());
            String groupName = "";
            if (member != null) {
                FamilyGroupEntity group = groupMap.get(member.getGroupId());
                if (group != null) {
                    Optional<UserEntity> owner = userRepository.findById(group.getOwnerId());
                    groupName = owner.map(o -> "Семья " + o.getName().split(" ")[0])
                            .orElse("Группа #" + group.getId());
                }
            }
            return Map.<String, Object>of(
                    "user_id", u.getId(),
                    "phone_number", u.getPhoneNumber(),
                    "name", u.getName() != null ? u.getName() : "",
                    "group_id", member != null ? member.getGroupId() : 0L,
                    "group_name", groupName,
                    "role", member != null ? member.getRole().name() : "MEMBER",
                    "status", member != null ? "ACTIVE" : "INACTIVE"
            );
        }).collect(Collectors.toList());
    }
}
