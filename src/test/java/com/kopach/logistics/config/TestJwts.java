package com.kopach.logistics.config;

import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.util.List;
import java.util.Map;

// Токен, какой выдал бы Keycloak: sub = id пользователя в Keycloak, роли в realm_access.roles.
// В тестах его возвращает подменённый JwtDecoder, поэтому подпись не нужна, а
// KeycloakJwtConverter и правила SecurityConfig работают по-настоящему
public final class TestJwts {

    private TestJwts() {
    }

    public static Jwt keycloakJwt(String subject, String... realmRoles) {
        return Jwt.withTokenValue("test-token")
                .header("alg", "none")
                .subject(subject)
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(300))
                // default-roles-... Keycloak добавляет всем; конвертер должен его отбросить
                .claim("realm_access", Map.of("roles",
                        concat(List.of(realmRoles), "default-roles-citylogistics")))
                .build();
    }

    private static List<String> concat(List<String> roles, String extra) {
        return java.util.stream.Stream.concat(roles.stream(), java.util.stream.Stream.of(extra)).toList();
    }
}
