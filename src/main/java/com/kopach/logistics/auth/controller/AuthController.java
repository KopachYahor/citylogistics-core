package com.kopach.logistics.auth.controller;

import com.kopach.logistics.auth.dto.AuthResponse;
import com.kopach.logistics.auth.dto.RegisterRequest;
import com.kopach.logistics.auth.dto.UserResponse;
import com.kopach.logistics.auth.entity.User;
import com.kopach.logistics.auth.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Auth", description = "Регистрация и текущий пользователь. Вход — на странице Keycloak")
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    // Пустой @SecurityRequirements: эндпоинт открыт, токен не нужен
    @SecurityRequirements
    @Operation(summary = "Регистрация",
            description = "Создаёт пользователя в Keycloak и в core, для DRIVER ещё профиль в driver-service")
    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@RequestBody @Valid RegisterRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(authService.register(request));
    }

    // Входа здесь нет: пользователь входит на странице Keycloak и получает токен там

    // user не null: без токена SecurityConfig раньше ответит 401
    @Operation(summary = "Текущий пользователь")
    @GetMapping("/me")
    public ResponseEntity<UserResponse> me(@AuthenticationPrincipal User user) {
        return ResponseEntity.ok(UserResponse.from(user));
    }
}