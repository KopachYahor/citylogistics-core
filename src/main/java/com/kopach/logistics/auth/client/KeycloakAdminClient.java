package com.kopach.logistics.auth.client;

import com.kopach.logistics.auth.entity.Role;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Map;

// Admin REST API Keycloak: core создаёт пользователя при регистрации и удаляет его при откате.
// core входит в Keycloak как служебный клиент citylogistics-core (grant_type=client_credentials);
// права ему даны в realm-export.json: realm-management -> manage-users, view-realm
@Component
public class KeycloakAdminClient {

    private static final Logger log = LoggerFactory.getLogger(KeycloakAdminClient.class);

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(2);
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(5);

    private final RestClient restClient;
    private final String realm;
    private final String clientId;
    private final String clientSecret;

    public KeycloakAdminClient(@Value("${keycloak.url}") String keycloakUrl,
                               @Value("${keycloak.realm}") String realm,
                               @Value("${keycloak.client-id}") String clientId,
                               @Value("${keycloak.client-secret}") String clientSecret) {
        if (clientSecret == null || clientSecret.isBlank()) {
            // Иначе приложение запустится, а сломается только первая регистрация, причём с ответом 503
            throw new IllegalStateException(
                    "keycloak.client-secret (KEYCLOAK_CORE_CLIENT_SECRET) must not be empty");
        }
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(CONNECT_TIMEOUT);
        requestFactory.setReadTimeout(READ_TIMEOUT);
        this.restClient = RestClient.builder()
                .baseUrl(keycloakUrl)
                .requestFactory(requestFactory)
                .build();
        this.realm = realm;
        this.clientId = clientId;
        this.clientSecret = clientSecret;
    }

    // Создаёт пользователя с паролем и ролью, возвращает его id в Keycloak (он же sub в токенах)
    public String createUser(String email, String password, String name, Role role) {
        String token = serviceToken();

        Map<String, Object> user = Map.of(
                "username", email,
                "email", email,
                "firstName", name,
                "enabled", true,
                // Подтверждать почту письмом нечем, поэтому считаем её подтверждённой
                "emailVerified", true,
                "credentials", List.of(Map.of("type", "password", "value", password, "temporary", false))
        );

        ResponseEntity<Void> created;
        try {
            created = restClient.post()
                    .uri("/admin/realms/{realm}/users", realm)
                    .headers(h -> h.setBearerAuth(token))
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(user)
                    .retrieve()
                    .toBodilessEntity();
        } catch (HttpClientErrorException.Conflict e) {
            // В Keycloak такой email уже есть (например, остался после сбоя)
            throw new IllegalArgumentException("Email already registered");
        } catch (HttpClientErrorException.BadRequest e) {
            // Keycloak отверг данные (например, «!» или скобки в имени). Это ошибка ввода,
            // а не недоступность сервиса: клиент должен получить 400 с понятной причиной, а не 503
            throw new IllegalArgumentException(badRequestMessage(e.getResponseBodyAsString()));
        }

        // Keycloak отвечает 201 и адресом нового пользователя: .../users/{id}
        URI location = created.getHeaders().getLocation();
        if (location == null) {
            throw new IllegalStateException("Keycloak did not return the user location");
        }
        String path = location.getPath();
        String keycloakId = path.substring(path.lastIndexOf('/') + 1);

        try {
            assignRealmRole(token, keycloakId, role);
        } catch (RuntimeException e) {
            // Пользователь без роли не сможет ничего сделать: убираем его и сообщаем об ошибке
            deleteUser(keycloakId);
            throw e;
        }
        log.info("Keycloak user created: id={}, role={}", keycloakId, role);
        return keycloakId;
    }

    // Ответ Keycloak на неверные данные: {"field":"firstName","errorMessage":"error-person-name-invalid-character",...}.
    // Переводим имя поля в имя из RegisterRequest, чтобы ошибка выглядела как обычная ошибка валидации
    static String badRequestMessage(String responseBody) {
        String body = responseBody == null ? "" : responseBody;
        if (body.contains("\"field\":\"firstName\"")) {
            return "name: contains characters that are not allowed";
        }
        if (body.contains("\"field\":\"email\"")) {
            return "email: is not accepted";
        }
        if (body.contains("\"field\":\"username\"")) {
            return "email: is not accepted as a login";
        }
        return "Invalid registration data";
    }

    // Компенсирующее действие при откате регистрации. Ошибку только пишем в лог:
    // исходная ошибка регистрации важнее, а «осиротевшего» пользователя видно в консоли Keycloak
    public void deleteUser(String keycloakId) {
        try {
            restClient.delete()
                    .uri("/admin/realms/{realm}/users/{id}", realm, keycloakId)
                    .headers(h -> h.setBearerAuth(serviceToken()))
                    .retrieve()
                    .toBodilessEntity();
            log.info("Keycloak user deleted: id={}", keycloakId);
        } catch (RuntimeException e) {
            log.error("Could not delete Keycloak user {}: {}", keycloakId, e.toString());
        }
    }

    private void assignRealmRole(String token, String keycloakId, Role role) {
        // Чтобы назначить роль, нужен её id: берём описание роли по имени
        Map<?, ?> roleRepresentation = restClient.get()
                .uri("/admin/realms/{realm}/roles/{role}", realm, role.name())
                .headers(h -> h.setBearerAuth(token))
                .retrieve()
                .body(Map.class);
        if (roleRepresentation == null) {
            throw new IllegalStateException("Keycloak role not found: " + role);
        }

        restClient.post()
                .uri("/admin/realms/{realm}/users/{id}/role-mappings/realm", realm, keycloakId)
                .headers(h -> h.setBearerAuth(token))
                .contentType(MediaType.APPLICATION_JSON)
                .body(List.of(Map.of("id", roleRepresentation.get("id"), "name", role.name())))
                .retrieve()
                .toBodilessEntity();
    }

    // Токен самого core (не пользователя): client credentials по секрету клиента
    private String serviceToken() {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "client_credentials");
        form.add("client_id", clientId);
        form.add("client_secret", clientSecret);

        Map<?, ?> response = restClient.post()
                .uri("/realms/{realm}/protocol/openid-connect/token", realm)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .retrieve()
                .body(Map.class);
        if (response == null || !(response.get("access_token") instanceof String token)) {
            throw new IllegalStateException("Keycloak did not return a service token");
        }
        return token;
    }
}
