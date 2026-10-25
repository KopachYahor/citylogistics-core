# CityLogistics — Core Service

Основной сервис логистической платформы. Отвечает за аутентификацию (JWT), управление заказами и общается с микросервисом водителей через Apache Kafka.

## Архитектура (Event-Driven)
1. Клиент создает заказ → `core` сохраняет его (`NEW`) и публикует `order.created`.
2. `driver-service` назначает водителя и публикует `driver.assigned`.
3. `core` ловит событие, записывает `driver_id` и меняет статус на `ACCEPTED`.
4. Водитель меняет статусы через API (`IN_TRANSIT` → `ARRIVED` → `DELIVERED`), `core` валидирует переходы и шлет `order.status-changed`.

## Стек
- Java 21, Spring Boot 3.3
- Spring Security + JWT (роли `CLIENT` и `DRIVER`)
- Spring Data JPA, PostgreSQL 15, Flyway (V1-V7)
- Apache Kafka, Docker Compose, Testcontainers

## Ключевые фичи
- **State Machine (Конечный автомат):** Enum `OrderStatus` с методом `canTransitionTo` запрещает недопустимые переходы (например, перескок с `ACCEPTED` на `DELIVERED`).
- **Отказоустойчивость (Сага):** При регистрации водителя, если `driver-service` падает, `core` откатывает транзакцию и удаляет `User` (компенсирующее действие).
- **Безопасность:** Кастомный `JwtAuthenticationFilter`, пароли хешируются BCrypt.

## Запуск
1. `docker compose up -d` (поднимет 2 базы PostgreSQL, Kafka, Kafka-UI).
2. `./mvnw spring-boot:run` (порт 8080, Flyway применит миграции).