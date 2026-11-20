package com.kopach.logistics.auth.service;

import com.kopach.logistics.auth.client.KeycloakAdminClient;
import com.kopach.logistics.auth.dto.AuthResponse;
import com.kopach.logistics.auth.dto.RegisterRequest;
import com.kopach.logistics.auth.entity.Role;
import com.kopach.logistics.auth.entity.User;
import com.kopach.logistics.auth.repository.UserRepository;
import com.kopach.logistics.common.ServiceUnavailableException;
import com.kopach.logistics.order.client.DriverServiceClient;
import com.kopach.logistics.order.client.dto.DriverDto;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Locale;

// Регистрация в трёх системах по очереди: Keycloak (логин и пароль) -> наша база (User) ->
// driver-service (только для водителя). Если шаг упал, удаляем то, что уже создали
// (компенсирующие действия). Входа здесь нет: токен выдаёт Keycloak
@Service
public class AuthService {

    private final UserRepository userRepository;
    private final KeycloakAdminClient keycloakAdminClient;
    private final DriverServiceClient driverServiceClient;

    public AuthService(UserRepository userRepository,
                       KeycloakAdminClient keycloakAdminClient,
                       DriverServiceClient driverServiceClient) {
        this.userRepository = userRepository;
        this.keycloakAdminClient = keycloakAdminClient;
        this.driverServiceClient = driverServiceClient;
    }

    public AuthResponse register(RegisterRequest request) {
        // До создания чего-либо: иначе пришлось бы сразу откатывать
        if (request.getRole() == Role.DRIVER
                && (request.getPhone() == null || request.getPhone().isBlank())) {
            throw new IllegalArgumentException("phone: required for drivers");
        }
        String email = normalizeEmail(request.getEmail());
        if (userRepository.findByEmail(email).isPresent()) {
            throw new IllegalArgumentException("Email already registered");
        }

        // 1. Keycloak: логин, пароль и роль
        String keycloakId;
        try {
            keycloakId = keycloakAdminClient.createUser(email, request.getPassword(), request.getName(), request.getRole());
        } catch (IllegalArgumentException e) {
            throw e; // email уже есть в Keycloak
        } catch (RuntimeException e) {
            throw new ServiceUnavailableException("Authentication service is unavailable, try again later", e);
        }

        // 2. Наша база
        User user = new User(email, keycloakId, request.getRole(), LocalDateTime.now());
        try {
            userRepository.save(user);
        } catch (DataIntegrityViolationException e) {
            // Гонка: два запроса с одним email оба прошли проверку выше
            keycloakAdminClient.deleteUser(keycloakId);
            throw new IllegalArgumentException("Email already registered");
        }

        // 3. driver-service, только для водителя
        if (request.getRole() == Role.DRIVER) {
            try {
                DriverDto driver = driverServiceClient.createDriver(user.getId(), request.getName(), request.getPhone());
                user.setDriverId(driver.getId());
                userRepository.save(user);
            } catch (Exception e) {
                // Водителя создать не удалось: откатываем оба предыдущих шага
                userRepository.delete(user);
                keycloakAdminClient.deleteUser(keycloakId);
                throw new ServiceUnavailableException("Driver service is unavailable, try again later", e);
            }
        }

        return new AuthResponse("Registered successfully");
    }

    // Ivan@Mail.by и ivan@mail.by — один адрес
    private String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
