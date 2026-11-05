package com.kopach.logistics.auth.dto;

import com.kopach.logistics.auth.entity.Role;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public class RegisterRequest {

    @NotBlank
    @Size(min = 3, max = 255)
    private String name;

    @NotBlank
    @Size(min = 6, max = 100)
    private String password;

    @NotBlank
    @Email
    @Size(max = 255) // users.email VARCHAR(255)
    private String email;

    // Обязателен только для DRIVER: эту проверку делает AuthService,
    // потому что аннотация не знает, какая роль выбрана.
    @Size(max = 50) // drivers.phone VARCHAR(50) в driver-service
    private String phone;

    @NotNull
    private Role role;

    public RegisterRequest() {
    }

    public RegisterRequest(String name, String email, String password, String phone, Role role) {
        this.name = name;
        this.email = email;
        this.password = password;
        this.phone = phone;
        this.role = role;
    }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }

    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }

    public Role getRole() { return role; }
    public void setRole(Role role) { this.role = role; }
}