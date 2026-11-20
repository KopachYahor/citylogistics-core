package com.kopach.logistics.order.controller;

import com.kopach.logistics.auth.entity.Role;
import com.kopach.logistics.auth.entity.User;
import com.kopach.logistics.auth.repository.UserRepository;
import com.kopach.logistics.config.AbstractPostgresTest;
import com.kopach.logistics.config.TestJwts;
import com.kopach.logistics.order.repository.OrderRepository;
import com.kopach.logistics.order.service.OrderEventPublisher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;

import static org.mockito.Mockito.when;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class OrderControllerSecurityTest extends AbstractPostgresTest {

    private static final String ORDER_JSON =
            "{\"fromAddress\":\"Minsk\",\"toAddress\":\"Brest\",\"price\":100}";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private OrderRepository orderRepository;

    // Вместо проверки подписи ключом Keycloak: строка токена -> готовый Jwt (см. TestJwts)
    @MockBean
    private JwtDecoder jwtDecoder;

    // Kafka здесь не нужна
    @MockBean
    private OrderEventPublisher eventPublisher;

    private String clientToken;
    private String driverToken;

    @BeforeEach
    void setUp() {
        orderRepository.deleteAll();
        userRepository.deleteAll();
        clientToken = tokenFor("client@test.local", Role.CLIENT);
        driverToken = tokenFor("driver@test.local", Role.DRIVER);
    }

    @Test
    void withoutToken_shouldReturn401() throws Exception {
        mockMvc.perform(post("/api/orders").contentType(MediaType.APPLICATION_JSON).content(ORDER_JSON))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void client_canCreateOrder() throws Exception {
        mockMvc.perform(post("/api/orders").header("Authorization", "Bearer " + clientToken)
                        .contentType(MediaType.APPLICATION_JSON).content(ORDER_JSON))
                .andExpect(status().isCreated());
    }

    @Test
    void client_cannotChangeOrderStatus() throws Exception {
        mockMvc.perform(patch("/api/orders/1/status").header("Authorization", "Bearer " + clientToken)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"IN_TRANSIT\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void client_cannotSeeAssignedOrders() throws Exception {
        mockMvc.perform(get("/api/orders/assigned").header("Authorization", "Bearer " + clientToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void driver_cannotCreateOrder() throws Exception {
        mockMvc.perform(post("/api/orders").header("Authorization", "Bearer " + driverToken)
                        .contentType(MediaType.APPLICATION_JSON).content(ORDER_JSON))
                .andExpect(status().isForbidden());
    }

    @Test
    void driver_canSeeAssignedOrders() throws Exception {
        mockMvc.perform(get("/api/orders/assigned").header("Authorization", "Bearer " + driverToken))
                .andExpect(status().isOk());
    }

    private String tokenFor(String email, Role role) {
        String keycloakId = "kc-" + email;
        userRepository.save(new User(email, keycloakId, role, LocalDateTime.now()));
        // В Bearer-токене допустимы только символы base64url, поэтому без email и @
        String token = "token-" + role.name().toLowerCase();
        when(jwtDecoder.decode(token)).thenReturn(TestJwts.keycloakJwt(keycloakId, role.name()));
        return token;
    }
}
