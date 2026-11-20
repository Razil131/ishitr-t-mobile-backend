package ru.tmobile.backend.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import ru.tmobile.backend.dto.auth.LogoutRequest;
import ru.tmobile.backend.dto.auth.RefreshRequest;
import ru.tmobile.backend.dto.auth.RefreshResponse;
import ru.tmobile.backend.dto.auth.RequestCodeRequest;
import ru.tmobile.backend.dto.auth.RequestCodeResponse;
import ru.tmobile.backend.dto.auth.VerifyCodeRequest;
import ru.tmobile.backend.dto.auth.VerifyCodeResponse;
import ru.tmobile.backend.service.AuthService;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/request-code")
    public ResponseEntity<RequestCodeResponse> requestCode(@Valid @RequestBody RequestCodeRequest request) {
        RequestCodeResponse response = authService.requestCode(request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/verify-code")
    public ResponseEntity<VerifyCodeResponse> verifyCode(@Valid @RequestBody VerifyCodeRequest request) {
        VerifyCodeResponse response = authService.verifyCode(request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/refresh")
    public ResponseEntity<RefreshResponse> refresh(@Valid @RequestBody RefreshRequest request) {
        RefreshResponse response = authService.refresh(request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@Valid @RequestBody LogoutRequest request) {
        authService.logout(request);
        return ResponseEntity.noContent().build();
    }
}
