package com.kopach.logistics.auth.entity;

import jakarta.persistence.*;

import java.time.LocalDateTime;

// Пользователь приложения. Пароль и вход — в Keycloak, здесь только то, что нужно core:
// email, роль, связь с Keycloak (keycloakId = sub в токене) и с водителем в driver-service
@Entity
@Table(name = "users")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String email;

    // id пользователя в Keycloak (UUID). По нему KeycloakJwtConverter находит User по токену
    @Column(name = "keycloak_id", unique = true)
    private String keycloakId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "driver_id")
    private Long driverId;

    public User() {
    }

    public User(String email, String keycloakId, Role role, LocalDateTime createdAt) {
        this.email = email;
        this.keycloakId = keycloakId;
        this.role = role;
        this.createdAt = createdAt;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getKeycloakId() { return keycloakId; }
    public void setKeycloakId(String keycloakId) { this.keycloakId = keycloakId; }

    public Role getRole() { return role; }
    public void setRole(Role role) { this.role = role; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public Long getDriverId() { return driverId; }
    public void setDriverId(Long driverId) { this.driverId = driverId; }
}
