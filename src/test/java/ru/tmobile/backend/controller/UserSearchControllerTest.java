package ru.tmobile.backend.controller;

import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import ru.tmobile.backend.dto.familyGroup.Contact;
import ru.tmobile.backend.dto.familyGroup.UserSearchResponse;
import ru.tmobile.backend.exception.BusinessRuleViolationException;
import ru.tmobile.backend.exception.GlobalExceptionHandler;
import ru.tmobile.backend.security.AuthContext;
import ru.tmobile.backend.service.UserSearchService;

class UserSearchControllerTest {
    MockMvc mockMvc;
    UserSearchService userSearchService;

    @BeforeEach
    void setUp() {
        userSearchService = mock(UserSearchService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new UserSearchController(userSearchService))
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();
    }

    @AfterEach
    void tearDown() {
        AuthContext.clear();
    }

    @Test
    void searchReturnsResultsField() throws Exception {
        AuthContext.set(new AuthContext.UserContext(1L, "OWNER"));
        when(userSearchService.searchByPhone(eq("+79001112233"), eq(1L)))
            .thenReturn(new UserSearchResponse(List.of(
                new Contact(88L, "+79001112233", "Мария"))));

        mockMvc.perform(get("/api/v1/users/search").param("phone", "+79001112233"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.results.length()").value(1))
            .andExpect(jsonPath("$.results[0].user_id").value(88))
            .andExpect(jsonPath("$.results[0].phone_number").value("+79001112233"))
            .andExpect(jsonPath("$.results[0].name").value("Мария"));
    }

    @Test
    void searchReturnsNullNameWhenUserHasNoName() throws Exception {
        AuthContext.set(new AuthContext.UserContext(1L, "OWNER"));
        when(userSearchService.searchByPhone(eq("+79001112233"), eq(1L)))
            .thenReturn(new UserSearchResponse(List.of(
                new Contact(88L, "+79001112233", null))));

        mockMvc.perform(get("/api/v1/users/search").param("phone", "+79001112233"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.results[0].name").value(nullValue()));
    }

    @Test
    void searchReturnsEmptyResultsForUnknownPhone() throws Exception {
        AuthContext.set(new AuthContext.UserContext(1L, "OWNER"));
        when(userSearchService.searchByPhone(eq("+79001112233"), eq(1L)))
            .thenReturn(new UserSearchResponse(List.of()));

        mockMvc.perform(get("/api/v1/users/search").param("phone", "+79001112233"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.results.length()").value(0));
    }

    @Test
    void searchUsesCallerIdFromAuthContext() throws Exception {
        AuthContext.set(new AuthContext.UserContext(42L, "OWNER"));
        when(userSearchService.searchByPhone(eq("+79001112233"), eq(42L)))
            .thenReturn(new UserSearchResponse(List.of()));

        mockMvc.perform(get("/api/v1/users/search").param("phone", "+79001112233"))
            .andExpect(status().isOk());

        verify(userSearchService).searchByPhone("+79001112233", 42L);
    }

    @Test
    void searchReturns400WhenServiceThrowsPhoneTooShort() throws Exception {
        when(userSearchService.searchByPhone(eq("+79"), eq(null)))
            .thenThrow(new BusinessRuleViolationException(
                "PHONE_TOO_SHORT", "Phone is too short", HttpStatus.BAD_REQUEST));

        mockMvc.perform(get("/api/v1/users/search").param("phone", "+79"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error.code").value("PHONE_TOO_SHORT"));
    }

    @Test
    void searchReturns400WhenServiceThrowsInvalidPhoneFormat() throws Exception {
        when(userSearchService.searchByPhone(eq("abc"), eq(null)))
            .thenThrow(new BusinessRuleViolationException(
                "INVALID_PHONE_FORMAT", "Invalid phone format", HttpStatus.BAD_REQUEST));

        mockMvc.perform(get("/api/v1/users/search").param("phone", "abc"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error.code").value("INVALID_PHONE_FORMAT"));
    }
}