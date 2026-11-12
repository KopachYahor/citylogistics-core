package com.kopach.logistics.order.client;

import com.kopach.logistics.order.client.dto.CreateDriverServiceRequest;
import com.kopach.logistics.order.client.dto.DriverDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Duration;

@Component
public class DriverServiceClient {

    private static final Logger log = LoggerFactory.getLogger(DriverServiceClient.class);

    // Без таймаутов зависший driver-service держал бы поток запроса бесконечно
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(2);
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(5);

    // Проверяется в driver-service (InternalApiKeyFilter)
    private static final String API_KEY_HEADER = "X-Internal-Api-Key";

    private final RestClient restClient;

    public DriverServiceClient(@Value("${services.driver-service.url}") String baseUrl,
                               @Value("${services.driver-service.api-key}") String apiKey) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(CONNECT_TIMEOUT);
        requestFactory.setReadTimeout(READ_TIMEOUT);

        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(requestFactory)
                .defaultHeader(API_KEY_HEADER, apiKey)
                .build();
        log.info("DriverServiceClient configured with base URL: {}", baseUrl);
    }

    public DriverDto createDriver(Long userId, String name, String phone) {
        CreateDriverServiceRequest request = new CreateDriverServiceRequest(userId, name, phone);
        log.info("Creating driver in driver-service: userId={}, name={}", userId, name);

        return restClient.post()
                .uri("/api/drivers")
                .body(request)
                .retrieve()
                .body(DriverDto.class);
    }
}