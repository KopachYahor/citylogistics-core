package com.kopach.logistics.auth.controller;

import com.kopach.logistics.auth.dto.AuthResponse;
import com.kopach.logistics.auth.dto.LoginRequest;
import com.kopach.logistics.auth.dto.RegisterRequest;
import com.kopach.logistics.auth.dto.UserResponse;
import com.kopach.logistics.auth.entity.User;
import com.kopach.logistics.auth.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@RequestBody @Valid RegisterRequest request) {
        // 201 Created: создан новый пользователь
        return ResponseEntity.status(HttpStatus.CREATED).body(authService.register(request));
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@RequestBody @Valid LoginRequest request) {
        return ResponseEntity.ok(authService.login(request));
    }

    // Без токена сюда не попасть: SecurityConfig сам вернёт 401,
    // поэтому user здесь всегда не null.
    @GetMapping("/me")
    public ResponseEntity<UserResponse> me(@AuthenticationPrincipal User user) {
        return ResponseEntity.ok(UserResponse.from(user));
    }
}