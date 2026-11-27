package ru.tmobile.backend.service;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import ru.tmobile.backend.dto.familyGroup.Contact;
import ru.tmobile.backend.dto.familyGroup.UserSearchResponse;
import ru.tmobile.backend.entity.GroupMemberEntity.MemberStatus;
import ru.tmobile.backend.exception.BusinessRuleViolationException;
import ru.tmobile.backend.repository.GroupMemberRepository;
import ru.tmobile.backend.repository.UserRepository;

@Service
@RequiredArgsConstructor 
public class UserSearchService {
    private final GroupMemberRepository groupMemberRepository;
    private final UserRepository userRepository;

    private static final int MIN_DIGITS = 4;

    public UserSearchResponse searchByPhone(String phone, Long callerId) {
        validatePhone(phone);
        String digits = phone.replaceAll("\\D", "");

        List<Contact> results = userRepository.findByPhoneNumberIgnoreCase(phone).stream()
            .filter(user -> !user.getId().equals(callerId))
            .filter(user -> groupMemberRepository
                .findByUserIdAndStatus(user.getId(), MemberStatus.ACTIVE).isEmpty())
            .map(user -> new Contact(user.getId(), user.getPhoneNumber(), user.getName()))
            .toList();

        if (results.isEmpty() && digits.length() < 11) {
            results = userRepository.findTop10ByPhoneNumberStartingWithIgnoreCase(phone).stream()
            .filter(user -> !user.getId().equals(callerId))
            .filter(user -> groupMemberRepository
                .findByUserIdAndStatus(user.getId(), MemberStatus.ACTIVE).isEmpty())
            .map(user -> new Contact(user.getId(), user.getPhoneNumber(), user.getName()))
            .toList();
        }
        return new UserSearchResponse(results);
    }

    private void validatePhone(String phone) {
        if (phone == null || phone.isBlank()) {
            throw new BusinessRuleViolationException(
                "INVALID_PHONE_FORMAT", "Phone is blank", HttpStatus.BAD_REQUEST);
        }

        if (!phone.matches("\\+?\\d+")) {
            throw new BusinessRuleViolationException(
                "INVALID_PHONE_FORMAT", "Invalid phone format", HttpStatus.BAD_REQUEST);
        }

        String digits = phone.replaceAll("\\D", "");
        if (digits.length() < MIN_DIGITS) {
            throw new BusinessRuleViolationException(
                "PHONE_TOO_SHORT", "Phone is too short", HttpStatus.BAD_REQUEST);
        }
    }
}
