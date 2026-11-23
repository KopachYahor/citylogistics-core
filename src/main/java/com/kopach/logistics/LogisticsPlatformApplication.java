package com.kopach.logistics;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
// Для UnassignedOrderRetryScheduler (повтор назначения водителя)
@EnableScheduling
public class LogisticsPlatformApplication {

    public static void main(String[] args) {
        SpringApplication.run(LogisticsPlatformApplication.class, args);
    }
}