package com.kopach.logistics.config;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.io.IOException;
import java.util.List;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final KeycloakJwtConverter keycloakJwtConverter;

    public SecurityConfig(KeycloakJwtConverter keycloakJwtConverter) {
        this.keycloakJwtConverter = keycloakJwtConverter;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        // Нет токена, токен неверный, истёк или пользователь не зарегистрирован в приложении
        AuthenticationEntryPoint unauthorized = (request, response, e) ->
                writeError(response, HttpStatus.UNAUTHORIZED, "Unauthorized");

        http
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .csrf(csrf -> csrf.disable())
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // /error открыт: иначе любая ошибка превращается в 403
                        .requestMatchers("/actuator/health", "/api/auth/register", "/error").permitAll()
                        // Swagger UI и описание API открыты: это документация, данных там нет
                        .requestMatchers("/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs/**").permitAll()
                        // hasRole("CLIENT") проверяет право ROLE_CLIENT (его выдаёт KeycloakJwtConverter)
                        .requestMatchers(HttpMethod.POST, "/api/orders").hasRole("CLIENT")
                        .requestMatchers(HttpMethod.GET, "/api/orders/my").hasRole("CLIENT")
                        .requestMatchers(HttpMethod.PATCH, "/api/orders/*/cancel").hasRole("CLIENT")
                        .requestMatchers(HttpMethod.GET, "/api/orders/assigned").hasRole("DRIVER")
                        .requestMatchers(HttpMethod.PATCH, "/api/orders/*/status").hasRole("DRIVER")
                        .anyRequest().authenticated()
                )
                // Core — OAuth2 Resource Server: токены выдаёт Keycloak, core только проверяет их
                // открытым ключом Keycloak (настройки в spring.security.oauth2.resourceserver.jwt)
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(keycloakJwtConverter))
                        .authenticationEntryPoint(unauthorized)
                )
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(unauthorized)
                        .accessDeniedHandler((request, response, e) ->
                                writeError(response, HttpStatus.FORBIDDEN, "Access denied"))
                );

        return http.build();
    }

    // Ошибки фильтров Security не доходят до GlobalExceptionHandler
    private void writeError(HttpServletResponse response, HttpStatus status, String message) throws IOException {
        response.setStatus(status.value());
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"error\":\"" + message + "\"}");
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(List.of(
                "http://localhost:5500",
                "http://127.0.0.1:5500"
        ));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
