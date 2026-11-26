package com.kopach.logistics.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

// Описание API для Swagger UI (http://localhost:8080/swagger-ui.html). Эндпоинты springdoc
// строит сам по контроллерам, здесь — заголовок и схема авторизации: токен Keycloak в
// заголовке Authorization: Bearer <token> (кнопка Authorize в Swagger UI)
@Configuration
public class OpenApiConfig {

    static final String BEARER = "keycloakToken";

    @Bean
    public OpenAPI cityLogisticsOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("CityLogistics core API")
                        .version("1.0")
                        .description("""
                                Заказы и пользователи CityLogistics. Токен выдаёт Keycloak: войдите на сайте \
                                (http://localhost:5500) и возьмите его в консоли браузера \
                                `localStorage.getItem('token')` — он живёт 5 минут. \
                                Все ошибки приходят в формате {"error": "текст"}."""))
                .components(new Components().addSecuritySchemes(BEARER, new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT")
                        .description("Access token Keycloak (realm citylogistics)")))
                // По умолчанию все эндпоинты требуют токен; открытые помечены в контроллерах
                .addSecurityItem(new SecurityRequirement().addList(BEARER));
    }
}
