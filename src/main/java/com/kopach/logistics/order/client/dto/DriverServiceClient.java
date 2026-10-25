package com.kopach.logistics.order.client;

import com.kopach.logistics.order.client.dto.CreateDriverServiceRequest;
import com.kopach.logistics.order.client.dto.DriverDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class DriverServiceClient {

    private static final Logger log = LoggerFactory.getLogger(DriverServiceClient.class);

    private final RestClient restClient;

    public DriverServiceClient(@Value("${services.driver-service.url}") String baseUrl) {
        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
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