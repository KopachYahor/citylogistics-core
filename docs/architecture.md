# Architecture

## Overview

CityLogistics — распределённая система для доставки малых партий грузов.
Состоит из **двух Spring Boot сервисов**, которые общаются **асинхронно через Kafka**.
У каждого сервиса — своя PostgreSQL (паттерн *Database per Service*).

## Services

### citylogistics-core (порт 8080)

Основной сервис:

- **Auth Module:** регистрация, логин, JWT (`/api/auth/**`)
- **Order Module:** создание, просмотр, отмена заказов (`/api/orders/**`)
- **Kafka Producer:** публикует `OrderCreatedEvent` в топик `order.created`
- **Kafka Consumers (group `logistics-group`):**
   - `OrderProcessingListener` → `order.created` → ставит `status=PROCESSING`
   - `DriverAssignedListener` → `driver.assigned` → пишет `driver_id`, статус → `PROCESSING`

### citylogistics-driver-service (порт 8082)

Сервис водителей:

- **Driver Module:** CRUD водителей (`/api/drivers/**`)
- **Kafka Consumer (group `driver-service-group`):**
   - `OrderCreatedListener` → `order.created` → ищет свободного водителя, создаёт `OrderAssignment`, ставит `driver.status=BUSY`, публикует `driver.assigned`
- **Kafka Producer:** публикует `DriverAssignedEvent` в топик `driver.assigned`

### citylogistics-frontend (порт 5500)

HTML/CSS/JS, ходит к обоим бэкендам через REST.

## Data Flow

**Создание заказа:**

```
1. Клиент → POST /api/orders (монолит, 8080)
2. OrderService: сохраняет Order (status=NEW, driver_id=null)
3. OrderEventPublisher: публикует OrderCreatedEvent в топик order.created
4. Kafka раздаёт событие двум группам:
   ├── logistics-group (монолит):
   │   └── OrderProcessingListener → status=PROCESSING
   └── driver-service-group (driver-service):
       ├── OrderCreatedListener ищет свободного Driver
       ├── создаёт OrderAssignment
       ├── ставит Driver.status=BUSY
       └── DriverAssignedPublisher → публикует DriverAssignedEvent в driver.assigned
5. Монолит, группа logistics-group:
   └── DriverAssignedListener → Order.driver_id=Y, status=PROCESSING
```

## Kafka Topics

| Topic | Producer | Consumer | Group | Назначение |
|---|---|---|---|---|
| `order.created` | монолит | монолит | `logistics-group` | обновить статус заказа |
| `order.created` | монолит | driver-service | `driver-service-group` | назначить водителя |
| `driver.assigned` | driver-service | монолит | `logistics-group` | записать водителя в заказ |

**Ключ сообщения** — `orderId.toString()`. Все события одного заказа летят в одну партицию (гарантия порядка обработки).

**Разные consumer groups** на одном топике — обязательны. Иначе Kafka доставит сообщение только одному из двух сервисов.

## Data Model

### logistics (5432) — БД монолита

| Таблица | Поля |
|---|---|
| `users` | id, email, password_hash, role, created_at |
| `orders` | id, client_id, driver_id, status, from_address, to_address, price, created_at, updated_at |

### drivers (5433) — БД driver-service

| Таблица | Поля |
|---|---|
| `drivers` | id, name, phone, status (AVAILABLE/BUSY), rating, created_at |
| `order_assignments` | id, order_id, driver_id, assigned_at |

**Связи между БД нет.** Миграция `V5__drop_fk_orders_driver.sql` удалила FK `fk_orders_driver`. Это осознанное решение: у монолита своя таблица `users`, у driver-service свои `drivers`. FK между разными БД микросервисов не работает. `orders.driver_id` — ссылка на внешний идентификатор на уровне приложения, не БД.

## Почему Kafka, а не REST

Если бы монолит вызывал driver-service синхронно через REST:

- Клиент ждал бы, пока найдётся водитель, перед созданием заказа.
- Если driver-service упал — заказы не создаются.
- Сервисы связаны напрямую (нужен service discovery, retry, circuit breaker).

С Kafka:

- Заказ создаётся мгновенно.
- Если driver-service упал, заказ сохранится, водитель назначится позже.
- Сервисы развязаны: монолит не знает про driver-service, знает только про топик.

## Почему не полностью микросервисы

Auth и Orders живут в одном процессе (**монолит**) — осознанное решение:

- У них одна БД, общая логика (заказ принадлежит юзеру).
- Разделять их без веской причины — преждевременный оверинжиниринг.

Driver вынесен в отдельный сервис:

- Отдельная бизнес-область со своими данными.
- Естественно интегрируется через события.
- Демонстрирует паттерн *Database per Service* и event-driven communication.cd "C:\java labs"
