package com.kopach.logistics.common;

import java.time.LocalDateTime;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/health")
public class HealthController {
    @GetMapping
    public Map<String, Object> getStatus() {


        return Map.of(
                "status", "UP",
                "service", "logistics-platform",
                "timestamp", LocalDateTime.now().toString()
        );
    }
}
