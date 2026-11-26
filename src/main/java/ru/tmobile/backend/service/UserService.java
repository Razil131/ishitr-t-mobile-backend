package ru.tmobile.backend.service;

import java.util.Optional;

import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import ru.tmobile.backend.dto.auth.UpdateUserRequest;
import ru.tmobile.backend.dto.auth.UserProfile;
import ru.tmobile.backend.dto.auth.UserProfileResponse;
import ru.tmobile.backend.dto.familyGroup.GroupContext;
import ru.tmobile.backend.entity.GroupMemberEntity;
import ru.tmobile.backend.entity.UserEntity;
import ru.tmobile.backend.entity.GroupMemberEntity.MemberStatus;
import ru.tmobile.backend.repository.GroupMemberRepository;
import ru.tmobile.backend.repository.UserRepository;

@Service 
@RequiredArgsConstructor 
public class UserService {
    private final UserRepository userRepository;
    private final GroupMemberRepository groupMemberRepository;

    public UserProfileResponse getProfile(Long userId) {
        UserEntity user = userRepository.findById(userId)
            .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));
        UserProfile profile = new UserProfile(
            user.getId(),
            user.getPhoneNumber(),
            user.getName(),
            user.getCreatedAt()
        );
        GroupContext group = getActiveGroupContext(userId);
        return new UserProfileResponse(profile, group);
    }

    private GroupContext getActiveGroupContext(Long userId) {
        Optional<GroupMemberEntity> membershipOptional = groupMemberRepository
            .findByUserIdAndStatus(userId, MemberStatus.ACTIVE);

        if (membershipOptional.isEmpty()) {
            return null;
        }
        GroupMemberEntity membership = membershipOptional.get();

        Long groupId = membership.getGroupId();

        int memberCount = groupMemberRepository
            .findByGroupIdAndStatus(groupId, MemberStatus.ACTIVE).size();
        
        String role = membership.getRole().toString();

        return new GroupContext(groupId, role, memberCount);
    }
    
    public UserProfileResponse updateProfile(Long userId, UpdateUserRequest request) {
        UserEntity user = userRepository.findById(userId)
            .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));

        user.setName(request.name());
        userRepository.save(user);
        
        return getProfile(userId);
    }
}
