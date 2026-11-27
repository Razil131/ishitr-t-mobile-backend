package ru.tmobile.backend.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import ru.tmobile.backend.dto.familyGroup.UserSearchResponse;
import ru.tmobile.backend.entity.GroupMemberEntity.MemberStatus;
import ru.tmobile.backend.entity.UserEntity;
import ru.tmobile.backend.exception.BusinessRuleViolationException;
import ru.tmobile.backend.repository.GroupMemberRepository;
import ru.tmobile.backend.repository.UserRepository;

@ExtendWith(MockitoExtension.class)
class UserSearchServiceTest {
    @Mock GroupMemberRepository groupMemberRepository;
    @Mock UserRepository userRepository;

    UserSearchService svc;

    @BeforeEach
    void setUp() {
        svc = new UserSearchService(groupMemberRepository, userRepository);
    }

    private UserEntity user(long id, String phone, String name) {
        UserEntity u = new UserEntity();
        u.setId(id);
        u.setPhoneNumber(phone);
        u.setName(name);
        u.setCreatedAt(OffsetDateTime.now(ZoneOffset.UTC));
        return u;
    }

    @Test
    void searchReturnsMatchingUser() {
        UserEntity found = user(88L, "+79001112233", "Мария");
        when(userRepository.findByPhoneNumberIgnoreCase("+79001112233")).thenReturn(Optional.of(found));
        when(groupMemberRepository.findByUserIdAndStatus(88L, MemberStatus.ACTIVE))
            .thenReturn(Optional.empty());

        UserSearchResponse response = svc.searchByPhone("+79001112233", 1L);

        assertEquals(1, response.results().size());
        assertEquals(88L, response.results().get(0).userId());
        assertEquals("+79001112233", response.results().get(0).phoneNumber());
        assertEquals("Мария", response.results().get(0).name());
    }

    @Test
    void searchReturnsEmptyResultsForUnknownPhone() {
        when(userRepository.findByPhoneNumberIgnoreCase("+79001112233")).thenReturn(Optional.empty());

        UserSearchResponse response = svc.searchByPhone("+79001112233", 1L);

        assertEquals(0, response.results().size());
    }

    @Test
    void searchExcludesCaller() {
        UserEntity caller = user(1L, "+79001112233", "Я");
        when(userRepository.findByPhoneNumberIgnoreCase("+79001112233")).thenReturn(Optional.of(caller));

        UserSearchResponse response = svc.searchByPhone("+79001112233", 1L);

        assertEquals(0, response.results().size());
    }

    @Test
    void searchExcludesUserAlreadyInActiveGroup() {
        UserEntity busy = user(88L, "+79001112233", "Мария");
        when(userRepository.findByPhoneNumberIgnoreCase("+79001112233")).thenReturn(Optional.of(busy));
        when(groupMemberRepository.findByUserIdAndStatus(88L, MemberStatus.ACTIVE))
            .thenReturn(Optional.of(new ru.tmobile.backend.entity.GroupMemberEntity()));

        UserSearchResponse response = svc.searchByPhone("+79001112233", 1L);

        assertEquals(0, response.results().size());
    }

    @Test
    void searchFallsBackToPrefixForPartialInput() {
        UserEntity found = user(88L, "+79001112233", "Мария");
        when(userRepository.findByPhoneNumberIgnoreCase("+7900")).thenReturn(Optional.empty());
        when(userRepository.findTop10ByPhoneNumberStartingWithIgnoreCase("+7900"))
            .thenReturn(List.of(found));
        when(groupMemberRepository.findByUserIdAndStatus(88L, MemberStatus.ACTIVE))
            .thenReturn(Optional.empty());

        UserSearchResponse response = svc.searchByPhone("+7900", 1L);

        assertEquals(1, response.results().size());
        assertEquals(88L, response.results().get(0).userId());
    }

    @Test
    void searchRejectsPhoneWithTooFewDigits() {
        BusinessRuleViolationException ex = assertThrows(
            BusinessRuleViolationException.class,
            () -> svc.searchByPhone("+79", 1L));

        assertEquals("PHONE_TOO_SHORT", ex.getErrorCode());
        assertEquals(HttpStatus.BAD_REQUEST, ex.getHttpStatus());
    }

    @Test
    void searchRejectsInvalidPhoneFormat() {
        BusinessRuleViolationException ex = assertThrows(
            BusinessRuleViolationException.class,
            () -> svc.searchByPhone("abc", 1L));

        assertEquals("INVALID_PHONE_FORMAT", ex.getErrorCode());
        assertEquals(HttpStatus.BAD_REQUEST, ex.getHttpStatus());
    }
}