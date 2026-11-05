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

    // Без таймаутов вызов ждёт ответа бесконечно: если driver-service завис,
    // висит и регистрация, и поток сервера, который её обрабатывает.
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(2); // установить соединение
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(5);    // дождаться ответа

    private final RestClient restClient;

    public DriverServiceClient(@Value("${services.driver-service.url}") String baseUrl) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(CONNECT_TIMEOUT);
        requestFactory.setReadTimeout(READ_TIMEOUT);

        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(requestFactory)
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