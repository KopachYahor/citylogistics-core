# Architecture

## Overview

Logistics Platform — это backend-сервис для управления заказами на доставку.
Клиенты создают заказы, система автоматически назначает водителей,
статусы меняются асинхронно через Kafka.

## Components

### Auth Module
Отвечает за регистрацию, логин и выдачу JWT-токенов.
- `POST /api/auth/register` — регистрация
- `POST /api/auth/login` — логин, возвращает JWT
- Роли: CLIENT, DRIVER, ADMIN

### Order Module
Отвечает за создание и управление заказами.
- `POST /api/orders` — создать заказ (роль CLIENT)
- `GET /api/orders/my` — свои заказы
- `PATCH /api/orders/{id}/cancel` — отмена

### Dispatch Module
Слушает события из Kafka и назначает водителей.
- Слушает: `order.created`
- Публикует: `order.assigned`

### Notification Module
Слушает события и логирует уведомления.
- Слушает: `order.assigned`, `order.delivered`

## Data Flow

1. Клиент шлёт `POST /api/orders` → OrderService
2. OrderService валидирует, сохраняет в PostgreSQL, публикует `OrderCreated` в Kafka
3. DispatchService читает `OrderCreated`, ищет свободного водителя,
   меняет статус заказа, публикует `OrderAssigned`
4. NotificationService читает `OrderAssigned` и логирует уведомление

## Kafka Topics

| Topic | Producer | Consumer | Purpose |
|-------|----------|----------|---------|
| order.created | OrderService | DispatchService | Новый заказ создан |
| order.assigned | DispatchService | NotificationService | Водитель назначен |
| order.delivered | OrderService | NotificationService | Заказ доставлен |

## Database Schema

- `users` — пользователи (клиенты, водители, админы)
- `orders` — заказы
- `order_status_history` — история статусов

## Why Monolith, not Microservices

Для AQA-портфолио важно показать качественное **тестирование**, а не
оркестрацию микросервисов. Один процесс = один Spring-контекст = проще
писать интеграционные тесты с Testcontainers.


