package com.kopach.logistics.auth.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kopach.logistics.auth.dto.LoginRequest;
import com.kopach.logistics.auth.dto.RegisterRequest;
import com.kopach.logistics.auth.entity.Role;
import com.kopach.logistics.auth.repository.UserRepository;
import com.kopach.logistics.config.AbstractPostgresTest;
import com.kopach.logistics.order.repository.OrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * API-тесты регистрации, входа и /me через MockMvc.
 * Ѕаза Ч временна€ PostgreSQL из AbstractPostgresTest, поэтому deleteAll() безопасен.
 */
@SpringBootTest
@AutoConfigureMockMvc
class AuthControllerTest extends AbstractPostgresTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private OrderRepository orderRepository;

    @BeforeEach
    void cleanUp() {
        orderRepository.deleteAll();   // сначала заказы (ссылаютс€ на users)
        userRepository.deleteAll();    // потом пользователи
    }

    @Test
    void register_withValidData_shouldReturn201AndMessage() throws Exception {
        register(client("test@example.com", "secret1"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.message").value("Registered successfully"));
    }

    @Test
    void register_withBlankEmail_shouldReturn400() throws Exception {
        register(client("", "secret1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").exists());
    }

    @Test
    void register_withBlankPassword_shouldReturn400() throws Exception {
        register(client("test@example.com", ""))
                .andExpect(status().isBadRequest());
    }

    @Test
    void register_withDuplicateEmail_shouldReturn400WithError() throws Exception {
        register(client("dup@example.com", "secret1")).andExpect(status().isCreated());

        register(client("dup@example.com", "secret1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Email already registered"));
    }

    @Test
    void login_withWrongPassword_shouldReturn401() throws Exception {
        register(client("login@example.com", "secret1")).andExpect(status().isCreated());

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest("login@example.com", "wrong1"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("Invalid credentials"));
    }

    @Test
    void me_withoutToken_shouldReturn401() throws Exception {
        mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("Unauthorized"));
    }

    @Test
    void me_withToken_shouldReturnUserWithoutPassword() throws Exception {
        register(client("me@example.com", "secret1")).andExpect(status().isCreated());

        String loginJson = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest("me@example.com", "secret1"))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String token = objectMapper.readTree(loginJson).get("token").asText();

        mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("me@example.com"))
                .andExpect(jsonPath("$.role").value("CLIENT"))
                // DTO, а не сущность: ни хеша парол€, ни служебных полей Spring Security
                .andExpect(jsonPath("$.passwordHash").doesNotExist())
                .andExpect(jsonPath("$.authorities").doesNotExist());
    }

    private RegisterRequest client(String email, String password) {
        return new RegisterRequest("Test Client", email, password, null, Role.CLIENT);
    }

    private ResultActions register(RegisterRequest request) throws Exception {
        return mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)));
    }
}
