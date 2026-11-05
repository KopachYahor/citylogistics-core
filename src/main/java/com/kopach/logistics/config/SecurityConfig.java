package com.kopach.logistics.config;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.io.IOException;
import java.util.List;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    public SecurityConfig(JwtAuthenticationFilter jwtAuthenticationFilter) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .csrf(csrf -> csrf.disable())
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // /error — внутренний адрес Spring, куда он перенаправляет запрос при ошибке.
                        // Если его закрыть, любая ошибка (400, 404, 500) превращается в 403.
                        .requestMatchers("/api/health", "/api/auth/register", "/api/auth/login", "/error").permitAll()
                        // Роли проверяет бэкенд. Скрытые кнопки на фронтенде — не защита.
                        // hasRole("CLIENT") ищет у пользователя право "ROLE_CLIENT" (см. User.getAuthorities).
                        .requestMatchers(HttpMethod.POST, "/api/orders").hasRole("CLIENT")
                        .requestMatchers(HttpMethod.GET, "/api/orders/my").hasRole("CLIENT")
                        .requestMatchers(HttpMethod.PATCH, "/api/orders/*/cancel").hasRole("CLIENT")
                        .requestMatchers(HttpMethod.GET, "/api/orders/assigned").hasRole("DRIVER")
                        .requestMatchers(HttpMethod.PATCH, "/api/orders/*/status").hasRole("DRIVER")
                        // всё остальное (например, /api/auth/me) — любой вошедший пользователь
                        .anyRequest().authenticated()
                )
                .exceptionHandling(ex -> ex
                        // нет токена или токен неверный → 401 (кто ты?)
                        .authenticationEntryPoint((request, response, e) ->
                                writeError(response, HttpStatus.UNAUTHORIZED, "Unauthorized"))
                        // токен есть, но прав не хватает → 403 (тебе нельзя)
                        .accessDeniedHandler((request, response, e) ->
                                writeError(response, HttpStatus.FORBIDDEN, "Access denied"))
                )
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    // Ошибки из фильтров Security не доходят до GlobalExceptionHandler,
    // поэтому тело ответа в том же формате {"error": "..."} пишем вручную.
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

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}