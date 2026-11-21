package com.kopach.logistics.auth.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kopach.logistics.auth.client.KeycloakAdminClient;
import com.kopach.logistics.auth.dto.RegisterRequest;
import com.kopach.logistics.auth.entity.Role;
import com.kopach.logistics.auth.entity.User;
import com.kopach.logistics.auth.repository.UserRepository;
import com.kopach.logistics.config.AbstractPostgresTest;
import com.kopach.logistics.config.TestJwts;
import com.kopach.logistics.order.client.DriverServiceClient;
import com.kopach.logistics.order.client.dto.DriverDto;
import com.kopach.logistics.order.repository.OrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.web.client.ResourceAccessException;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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

    // Настоящий Keycloak в тестах не нужен: проверяем, что core правильно его вызывает
    @MockBean
    private KeycloakAdminClient keycloakAdminClient;

    @MockBean
    private DriverServiceClient driverServiceClient;

    // Строка токена -> готовый Jwt (см. TestJwts), подпись не проверяется
    @MockBean
    private JwtDecoder jwtDecoder;

    @BeforeEach
    void setUp() {
        orderRepository.deleteAll();   // сначала заказы (ссылаются на users)
        userRepository.deleteAll();
        when(keycloakAdminClient.createUser(anyString(), anyString(), anyString(), any(Role.class)))
                .thenReturn("kc-new-user");
    }

    @Test
    void register_withValidData_shouldCreateKeycloakUserAndLocalUser() throws Exception {
        register(client("test@example.com", "secret1"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.message").value("Registered successfully"));

        verify(keycloakAdminClient).createUser("test@example.com", "secret1", "Test Client", Role.CLIENT);
        User saved = userRepository.findByEmail("test@example.com").orElseThrow();
        assertThat(saved.getKeycloakId()).isEqualTo("kc-new-user");
        assertThat(saved.getRole()).isEqualTo(Role.CLIENT);
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
    void register_withDuplicateEmail_shouldReturn400WithoutSecondKeycloakUser() throws Exception {
        register(client("dup@example.com", "secret1")).andExpect(status().isCreated());

        register(client("dup@example.com", "secret1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Email already registered"));
        verify(keycloakAdminClient, times(1)).createUser(anyString(), anyString(), anyString(), any(Role.class));
    }

    @Test
    void email_shouldBeCaseInsensitive() throws Exception {
        register(client("Case@Example.com", "secret1")).andExpect(status().isCreated());
        verify(keycloakAdminClient).createUser(eq("case@example.com"), anyString(), anyString(), any(Role.class));

        // тот же адрес в другом регистре — это дубль
        register(client("case@example.COM", "secret1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Email already registered"));
    }

    @Test
    void register_whenKeycloakIsDown_shouldReturn503AndSaveNothing() throws Exception {
        when(keycloakAdminClient.createUser(anyString(), anyString(), anyString(), any(Role.class)))
                .thenThrow(new ResourceAccessException("Connection refused"));

        register(client("down@example.com", "secret1"))
                .andExpect(status().isServiceUnavailable());
        assertThat(userRepository.findByEmail("down@example.com")).isEmpty();
    }

    @Test
    void register_whenKeycloakRejectsData_shouldReturn400WithReason() throws Exception {
        // Например, «!» в имени: KeycloakAdminClient превращает 400 от Keycloak в IllegalArgumentException
        when(keycloakAdminClient.createUser(anyString(), anyString(), anyString(), any(Role.class)))
                .thenThrow(new IllegalArgumentException("name: contains characters that are not allowed"));

        register(new RegisterRequest("Test!", "bang@example.com", "secret1", null, Role.CLIENT))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("name: contains characters that are not allowed"));
        assertThat(userRepository.findByEmail("bang@example.com")).isEmpty();
    }

    @Test
    void register_driver_shouldLinkDriverFromDriverService() throws Exception {
        DriverDto driver = new DriverDto();
        driver.setId(42L);
        when(driverServiceClient.createDriver(any(), anyString(), anyString())).thenReturn(driver);

        register(driverRequest("driver@example.com"))
                .andExpect(status().isCreated());
        assertThat(userRepository.findByEmail("driver@example.com").orElseThrow().getDriverId()).isEqualTo(42L);
    }

    @Test
    void register_driver_whenDriverServiceFails_shouldRollBackLocalAndKeycloakUser() throws Exception {
        when(driverServiceClient.createDriver(any(), anyString(), anyString()))
                .thenThrow(new ResourceAccessException("Read timed out"));

        register(driverRequest("rollback@example.com"))
                .andExpect(status().isServiceUnavailable());

        // Компенсирующие действия: нет ни User у нас, ни пользователя в Keycloak
        assertThat(userRepository.findByEmail("rollback@example.com")).isEmpty();
        verify(keycloakAdminClient).deleteUser("kc-new-user");
    }

    @Test
    void me_withoutToken_shouldReturn401() throws Exception {
        mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("Unauthorized"));
    }

    @Test
    void me_withKeycloakToken_shouldReturnUserDto() throws Exception {
        userRepository.save(new User("me@example.com", "kc-me", Role.CLIENT, LocalDateTime.now()));
        when(jwtDecoder.decode("token-me")).thenReturn(TestJwts.keycloakJwt("kc-me", "CLIENT"));

        mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer token-me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("me@example.com"))
                .andExpect(jsonPath("$.role").value("CLIENT"))
                // DTO, а не сущность
                .andExpect(jsonPath("$.keycloakId").doesNotExist());
    }

    @Test
    void me_withTokenOfUnknownUser_shouldReturn401() throws Exception {
        // Токен валиден, но такого пользователя нет в нашей базе (например, создан в консоли Keycloak)
        when(jwtDecoder.decode("token-stranger")).thenReturn(TestJwts.keycloakJwt("kc-stranger", "CLIENT"));

        mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer token-stranger"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("Unauthorized"));
    }

    private RegisterRequest client(String email, String password) {
        return new RegisterRequest("Test Client", email, password, null, Role.CLIENT);
    }

    private RegisterRequest driverRequest(String email) {
        return new RegisterRequest("Test Driver", email, "secret1", "+375291112233", Role.DRIVER);
    }

    private ResultActions register(RegisterRequest request) throws Exception {
        return mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)));
    }
}
