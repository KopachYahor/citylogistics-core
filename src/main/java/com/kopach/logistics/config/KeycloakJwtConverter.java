package com.kopach.logistics.config;

import com.kopach.logistics.auth.entity.Role;
import com.kopach.logistics.auth.entity.User;
import com.kopach.logistics.auth.repository.UserRepository;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;

// Подпись, срок и iss токена Spring уже проверил. Здесь превращаем токен в пользователя приложения:
// роли берём из токена (их выдаёт Keycloak), а User из нашей базы находим по sub — id в Keycloak.
// Principal остаётся User, поэтому контроллеры с @AuthenticationPrincipal User не меняются
@Component
public class KeycloakJwtConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    private static final List<String> APP_ROLES = Arrays.stream(Role.values()).map(Enum::name).toList();

    private final UserRepository userRepository;

    public KeycloakJwtConverter(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        // Например, пользователь заведён в консоли Keycloak вручную, а не через регистрацию core
        User user = userRepository.findByKeycloakId(jwt.getSubject())
                .orElseThrow(() -> new InvalidBearerTokenException("User is not registered in the application"));

        return new UsernamePasswordAuthenticationToken(user, jwt, extractRoles(jwt));
    }

    // Keycloak кладёт роли realm в realm_access.roles: ["CLIENT", "default-roles-citylogistics", ...].
    // Оставляем только роли приложения и добавляем префикс ROLE_: его ждёт hasRole("CLIENT")
    static Collection<GrantedAuthority> extractRoles(Jwt jwt) {
        Map<String, Object> realmAccess = jwt.getClaimAsMap("realm_access");
        if (realmAccess == null || !(realmAccess.get("roles") instanceof Collection<?> roles)) {
            return List.of();
        }
        return roles.stream()
                .map(String::valueOf)
                .filter(APP_ROLES::contains)
                .map(role -> (GrantedAuthority) new SimpleGrantedAuthority("ROLE_" + role))
                .toList();
    }
}
