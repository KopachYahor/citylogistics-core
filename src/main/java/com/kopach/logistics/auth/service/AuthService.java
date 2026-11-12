package com.kopach.logistics.auth.service;

import com.kopach.logistics.auth.dto.AuthResponse;
import com.kopach.logistics.auth.dto.LoginRequest;
import com.kopach.logistics.auth.dto.RegisterRequest;
import com.kopach.logistics.auth.entity.Role;
import com.kopach.logistics.auth.entity.User;
import com.kopach.logistics.auth.repository.UserRepository;
import com.kopach.logistics.common.ServiceUnavailableException;
import com.kopach.logistics.order.client.DriverServiceClient;
import com.kopach.logistics.order.client.dto.DriverDto;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Locale;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final DriverServiceClient driverServiceClient;

    public AuthService(UserRepository userRepository,
                       PasswordEncoder passwordEncoder,
                       JwtService jwtService,
                       DriverServiceClient driverServiceClient) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.driverServiceClient = driverServiceClient;
    }

    public AuthResponse register(RegisterRequest request) {
        // До сохранения: иначе пришлось бы удалять уже созданного User
        if (request.getRole() == Role.DRIVER
                && (request.getPhone() == null || request.getPhone().isBlank())) {
            throw new IllegalArgumentException("phone: required for drivers");
        }
        String email = normalizeEmail(request.getEmail());
        if (userRepository.findByEmail(email).isPresent()) {
            throw new IllegalArgumentException("Email already registered");
        }

        User user = new User();
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        user.setRole(request.getRole());
        user.setCreatedAt(LocalDateTime.now());
        try {
            userRepository.save(user);
        } catch (DataIntegrityViolationException e) {
            // Гонка: два запроса с одним email оба прошли проверку выше
            throw new IllegalArgumentException("Email already registered");
        }

        if (request.getRole() == Role.DRIVER) {
            try {
                DriverDto driver = driverServiceClient.createDriver(
                        user.getId(),
                        request.getName(),
                        request.getPhone()
                );
                user.setDriverId(driver.getId());
                userRepository.save(user);
            } catch (Exception e) {
                // компенсирующее действие: водителя создать не удалось — удаляем и пользователя
                userRepository.delete(user);
                throw new ServiceUnavailableException("Driver service is unavailable, try again later", e);
            }
        }

        return new AuthResponse("Registered successfully");
    }

    public AuthResponse login(LoginRequest request) {
        // Один ответ на оба случая: нельзя узнать, зарегистрирован ли email
        User user = userRepository.findByEmail(normalizeEmail(request.getEmail()))
                .orElseThrow(() -> new BadCredentialsException("Invalid credentials"));
        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new BadCredentialsException("Invalid credentials");
        }
        String token = jwtService.generateToken(user.getEmail());
        return new AuthResponse("Login successful", token);
    }

    // Ivan@Mail.by и ivan@mail.by — один адрес
    private String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}