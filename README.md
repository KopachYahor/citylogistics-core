# CityLogistics — Core

Платформа для доставки малых партий грузов. Микросервисная архитектура на Spring Boot 3 + Kafka.

**`citylogistics-core`** — основной сервис: аутентификация, заказы, публикация событий.

## Архитектура

```
┌────────────────────────┐                    ┌───────────────────────────────┐
│  citylogistics-core    │                    │  citylogistics-driver-service │
│  (порт 8080)           │                    │  (порт 8082)                  │
│                        │                    │                               │
│  auth  (JWT)           │    ┌──────────┐    │  REST /api/drivers            │
│  order (создание/CRUD) │──▶ │  Kafka   │ ◀──│  слушает order.created        │
│                        │    │          │    │  публикует driver.assigned    │
│  слушает:              │    │ 2 топика │    │                               │
│   order.created        │    │          │    │                               │
│   driver.assigned      │    └──────────┘    │                               │
└─────────┬──────────────┘                    └─────────────┬─────────────────┘
          │                                                 │
          ▼                                                 ▼
   ┌──────────────┐                                  ┌──────────────┐
   │ PostgreSQL   │                                  │ PostgreSQL   │
   │ logistics    │                                  │ drivers      │
   │ (5432)       │                                  │ (5433)       │
   └──────────────┘                                  └──────────────┘
```

**Event-driven flow:**

1. Клиент создаёт заказ → монолит сохраняет (`status=NEW`) → публикует `order.created`
2. `driver-service` слушает → ищет свободного водителя → создаёт назначение → публикует `driver.assigned`
3. Монолит слушает `driver.assigned` → пишет `driver_id` в заказ, статус → `PROCESSING`

**Database per Service:** у монолита своя Postgres (5432), у driver-service — своя (5433). Сервисы не шарят таблицы, общаются только через Kafka.

## Стек

| Технология | Версия |
|---|---|
| Java | 21 (LTS) |
| Spring Boot | 3.3.0 |
| Spring Security + JJWT | 6.3 / 0.12.5 |
| Spring Data JPA + Hibernate | 6.5 |
| PostgreSQL | 15 |
| Flyway | 10 |
| Spring Kafka | 3.2 |
| Kafka (Confluent) | 7.6 |
| Docker + Docker Compose | — |
| Testcontainers | 1.21 |
| JUnit 5, Mockito, AssertJ | — |
| Maven | 3.9 (через `mvnw`) |

## Структура

```
src/main/java/com/kopach/logistics/
├── auth/                      — аутентификация, JWT, User, Role
│   ├── controller/            — AuthController
│   ├── service/               — AuthService, CustomUserDetailsService
│   ├── entity/                — User, Role
│   ├── repository/            — UserRepository
│   └── dto/                   — LoginRequest, RegisterRequest, AuthResponse
├── config/                    — SecurityConfig, JwtAuthenticationFilter
├── common/                    — HealthController, GlobalExceptionHandler
├── order/
│   ├── controller/            — OrderController
│   ├── service/               — OrderService, OrderEventPublisher,
│   │                            OrderProcessingListener, DriverAssignedListener
│   ├── repository/            — OrderRepository
│   ├── entity/                — Order, OrderStatus
│   └── dto/                   — CreateOrderRequest, OrderResponse, OrderCreatedEvent
├── driver/dto/                — DriverAssignedEvent (для десериализации Kafka)
└── LogisticsPlatformApplication.java
```

## Как запустить

### 1. Инфраструктура

```bash
cd citylogistics-core
docker compose up -d
```

Поднимаются 4 контейнера:

| Контейнер | Что | Порт |
|---|---|---|
| `logistics-postgres` | Postgres монолита | 5432 |
| `logistics-postgres-drivers` | Postgres driver-service | 5433 |
| `logistics-kafka` | Kafka | 29092 |
| `logistics-kafka-ui` | Kafka UI | 8090 |

### 2. Монолит

```bash
cd citylogistics-core
./mvnw spring-boot:run
```

Открывается `http://localhost:8080`.

### 3. Driver-service

```bash
cd citylogistics-driver-service
./mvnw spring-boot:run
```

Открывается `http://localhost:8082`.

### 4. Фронт

```bash
cd citylogistics-frontend
npx live-server --port=5500
```

Открывается `http://127.0.0.1:5500`.

### 5. Проверка

Kafka UI: `http://localhost:8090` — видны топики `order.created` и `driver.assigned`.

## REST API

### Auth (`/api/auth`)

| Метод | Путь | Описание |
|---|---|---|
| POST | `/api/auth/register` | Регистрация (email + password) |
| POST | `/api/auth/login` | Логин → JWT |
| GET | `/api/auth/me` | Текущий юзер по токену |

### Orders (`/api/orders`)

| Метод | Путь | Описание |
|---|---|---|
| POST | `/api/orders` | Создать заказ (JWT) |
| GET | `/api/orders/my` | Мои заказы (JWT) |
| PATCH | `/api/orders/{id}/cancel` | Отменить заказ (JWT) |

### Пример: логин + создание заказа

```bash
# 1. Логин
curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"test@test.ru","password":"password123"}'
# → {"message":"Login successful","token":"eyJ..."}

# 2. Создать заказ (подставь токен)
curl -X POST http://localhost:8080/api/orders \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer eyJ..." \
  -d '{"fromAddress":"Минск","toAddress":"Гомель","price":300}'
```

## Kafka

| Топик | Producer | Consumer | Группа | Назначение |
|---|---|---|---|---|
| `order.created` | монолит | монолит | `logistics-group` | обновить `Order.status` |
| `order.created` | монолит | driver-service | `driver-service-group` | назначить водителя |
| `driver.assigned` | driver-service | монолит | `logistics-group` | записать `Order.driverId` |

**Ключ сообщения** — `orderId.toString()`. Это гарантирует, что все события одного заказа попадут в одну партицию (порядок обработки).

**Consumer groups.** У монолита и driver-service **разные `group-id`** на общем топике `order.created`. Kafka раздаёт копию сообщения каждой группе. Если бы группа была одна — сообщение досталось бы только одному консьюмеру.

**Идемпотентность.** `OrderCreatedListener` в driver-service проверяет `existsByOrderId` перед назначением — если событие придёт дважды, второго водителя не назначит.

**Транзакции.** `DriverAssignedListener` и `OrderCreatedListener` помечены `@Transactional` — если сохранение упадёт, всё откатится.

## Тесты

```bash
cd citylogistics-core
./mvnw test
```

**12 тестов, все зелёные.**

| Класс | Тестов | Что проверяет |
|---|---|---|
| `AuthControllerTest` | 4 | register, login, валидация |
| `HealthControllerTest` | 1 | `/api/health` |
| `OrderRepositoryTest` | 2 | `findByClientId`, `findById` (Testcontainers + Postgres) |
| `OrderServiceTest` | 5 | createOrder, cancelOrder (Mockito) |

`OrderRepositoryTest` использует **Testcontainers** — поднимает реальный `postgres:15` в Docker, применяет Flyway-миграции, работает на настоящем диалекте, потом гасит контейнер.

## Что в планах

- `order.delivered` — событие от водителя при завершении доставки (endpoint `PATCH /api/drivers/orders/{id}/complete`)
- Retry + dead letter queue для Kafka listener-ов
- Refresh-токен для JWT (сейчас живёт 24 часа)
- CI (GitHub Actions): сборка + тесты на push
- Апгрейд Spring Boot до 3.4.x (там свежие патчи для Spring Framework и Tomcat)

## Лицензия

MIT