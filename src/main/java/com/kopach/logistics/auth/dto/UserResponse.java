package com.kopach.logistics.auth.dto;

import com.kopach.logistics.auth.entity.Role;
import com.kopach.logistics.auth.entity.User;

// Сущность User наружу не отдаём: в ней хеш пароля и служебные поля Spring Security
public class UserResponse {

    private final Long id;
    private final String email;
    private final Role role;
    private final Long driverId;

    public UserResponse(Long id, String email, Role role, Long driverId) {
        this.id = id;
        this.email = email;
        this.role = role;
        this.driverId = driverId;
    }

    public static UserResponse from(User user) {
        return new UserResponse(user.getId(), user.getEmail(), user.getRole(), user.getDriverId());
    }

    public Long getId() { return id; }
    public String getEmail() { return email; }
    public Role getRole() { return role; }
    public Long getDriverId() { return driverId; }
}
