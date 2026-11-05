package com.kopach.logistics.config;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Родитель для всех тестов, которым нужна база данных.
 *
 * Поднимает в Docker временную пустую PostgreSQL и подменяет на неё
 * spring.datasource.*. Поэтому тесты НЕ трогают рабочую БД из docker-compose,
 * а Flyway при каждом прогоне применяет все миграции на чистую базу.
 *
 * Важно: @DynamicPropertySource работает только в самом тестовом классе
 * или в его родителе. Поэтому тесты наследуются от этого класса (extends),
 * а не подключают его через @Import.
 */
public abstract class AbstractPostgresTest {

    // static: один контейнер на весь прогон тестов, а не новый на каждый класс
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15")
            .withDatabaseName("logistics_test")
            .withUsername("test")
            .withPassword("test");

    static {
        postgres.start();
    }

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }
}
