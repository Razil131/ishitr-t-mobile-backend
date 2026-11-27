package ru.tmobile.backend.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;
import ru.tmobile.backend.dto.familyGroup.UserSearchResponse;
import ru.tmobile.backend.security.AuthContext;
import ru.tmobile.backend.service.UserSearchService;


@RestController 
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor 
public class UserSearchController {
    private final UserSearchService userSearchService;

    @GetMapping("/search")
    public UserSearchResponse search(@RequestParam("phone") String phone) {
        return userSearchService.searchByPhone(phone, AuthContext.getUserId());
    }
    
}
