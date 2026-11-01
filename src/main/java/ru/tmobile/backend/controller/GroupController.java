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
@RequestMapping("/api/v1/groups")
@RequiredArgsConstructor
public class GroupController {

    private final FamilyGroupRepository groupRepository;
    private final GroupMemberRepository memberRepository;
    private final UserRepository userRepository;

    @GetMapping
    public List<Map<String, Object>> getGroups() {
        List<FamilyGroupEntity> groups = groupRepository.findAll();

        return groups.stream().map(g -> {
            List<GroupMemberEntity> members = memberRepository.findByGroupId(g.getId());
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
}
