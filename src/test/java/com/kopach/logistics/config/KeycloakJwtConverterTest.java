package com.kopach.logistics.config;

import com.kopach.logistics.auth.entity.Role;
import com.kopach.logistics.auth.entity.User;
import com.kopach.logistics.auth.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class KeycloakJwtConverterTest {

    private final UserRepository userRepository = mock(UserRepository.class);
    private final KeycloakJwtConverter converter = new KeycloakJwtConverter(userRepository);

    @Test
    void convert_shouldUseLocalUserAsPrincipalAndKeepOnlyAppRoles() {
        User user = new User("driver@test.local", "kc-1", Role.DRIVER, LocalDateTime.now());
        when(userRepository.findByKeycloakId("kc-1")).thenReturn(Optional.of(user));

        AbstractAuthenticationToken auth = converter.convert(TestJwts.keycloakJwt("kc-1", "DRIVER", "offline_access"));

        assertThat(auth.getPrincipal()).isSameAs(user);
        // offline_access и default-roles-citylogistics — служебные роли Keycloak, их отбрасываем
        assertThat(auth.getAuthorities()).extracting(GrantedAuthority::getAuthority).containsExactly("ROLE_DRIVER");
    }

    @Test
    void convert_withUnknownUser_shouldRejectToken() {
        when(userRepository.findByKeycloakId("kc-unknown")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> converter.convert(TestJwts.keycloakJwt("kc-unknown", "CLIENT")))
                .isInstanceOf(InvalidBearerTokenException.class);
    }

    @Test
    void extractRoles_withoutRealmAccess_shouldGiveNoRoles() {
        Jwt jwt = Jwt.withTokenValue("t").header("alg", "none").subject("kc-2")
                .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(60)).build();

        assertThat(KeycloakJwtConverter.extractRoles(jwt)).isEmpty();
    }
}
