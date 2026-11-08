# CityLogistics — Core Service

Учебный пет-проект: система доставки по городу. Клиент создаёт заказ, система сама
назначает свободного водителя, водитель проводит заказ по статусам до доставки.

Проект состоит из трёх репозиториев:

| Репозиторий | Что это | Порт |
|---|---|---|
| **citylogistics-core** (этот) | Пользователи, JWT, заказы | 8080, БД `logistics` на 5432 |
| [citylogistics-driver-service](https://github.com/KopachYahor/citylogistics-driver-service) | Водители и их статусы | 8082, БД `drivers` на 5433 |
| [citylogistics-frontend](https://github.com/KopachYahor/citylogistics-frontend) | HTML/CSS/Vanilla JS | 5500 |

Инфраструктура для всех сервисов описана в `docker-compose.yml` этого репозитория.

## Стек

Java 21, Spring Boot 3.3 (Web, Data JPA, Security, Validation, Kafka), PostgreSQL 15,
Flyway, JWT (JJWT) + BCrypt, Apache Kafka (KRaft, без ZooKeeper), Docker Compose,
JUnit 5, Mockito, MockMvc, Testcontainers.

## Архитектура

```
 Браузер ──REST──▶ core (8080) ──────────REST (только регистрация водителя)──────▶ driver-service (8082)
                    │   ▲                                                              │   ▲
                    │   │                                                              │   │
          PostgreSQL logistics             Kafka (localhost:29092)            PostgreSQL drivers
                    │   │            ┌───────────────────────────────┐                 │   │
                    │   └────────────│ driver.assigned               │◀────────────────┘   │
                    └───────────────▶│ order.created                 │─────────────────────┘
                                     │ order.status-changed          │
                                     └───────────────────────────────┘
```

- У каждого сервиса своя база (Database per Service). Внешних ключей между базами нет:
  `orders.driver_id` — это id водителя из другой базы, связь держит код, а не БД.
- Фронтенд общается **только с core** по REST. Про Kafka он не знает.
- Сервисы общаются через Kafka асинхронно. Синхронный REST-вызов один: при регистрации
  водителя core создаёт его профиль в driver-service. REST driver-service закрыт общим
  ключом: core передаёт его в заголовке `X-Internal-Api-Key`.

### Kafka

| Топик | Кто пишет | Кто читает (группа) | Зачем |
|---|---|---|---|
| `order.created` | core | driver-service (`driver-service-group`) | найти свободного водителя |
| `driver.assigned` | driver-service | core (`logistics-group`) | записать водителя в заказ, статус `ACCEPTED` |
| `order.status-changed` | core | driver-service (`driver-service-group`) | освободить водителя при `DELIVERED` / `CANCELLED` |

- Ключ сообщения во всех топиках — id заказа. Сообщения с одним ключом попадают в одну
  партицию, поэтому события одного заказа обрабатываются по порядку.
- Топики создаются автоматически, у каждого 1 партиция.
- События — JSON (`JsonSerializer`). Битое сообщение не блокирует консьюмер:
  `ErrorHandlingDeserializer` логирует его и пропускает.

## Жизненный цикл заказа

```
NEW ──▶ ACCEPTED ──▶ IN_TRANSIT ──▶ ARRIVED ──▶ DELIVERED
 │
 └──▶ CANCELLED   (клиент может отменить только заказ в статусе NEW)
```

1. Клиент создаёт заказ → core сохраняет его со статусом `NEW` и публикует `order.created`.
2. driver-service находит водителя `AVAILABLE`, ставит его `BUSY`, публикует `driver.assigned`.
3. core записывает `driverId` и ставит `ACCEPTED`. Если заказ уже не `NEW` (клиент успел
   отменить), назначение не принимается, а водитель освобождается.
4. Назначенный водитель меняет статус: `PATCH /api/orders/{id}/status`. Разрешён только
   следующий шаг (`OrderStatus.canTransitionTo`), core публикует `order.status-changed`.
5. При `DELIVERED` или `CANCELLED` driver-service снова делает водителя `AVAILABLE`.

## API

Все ошибки приходят в одном формате: `{"error": "текст"}`.

| Метод и адрес | Кто может | Что делает |
|---|---|---|
| `POST /api/auth/register` | все | Регистрация (ответ `201`). Тело: `name`, `email`, `password`, `role` (`CLIENT` / `DRIVER`), `phone` (обязателен для `DRIVER`). Email хранится в нижнем регистре |
| `POST /api/auth/login` | все | Вход по `email` и `password`, в ответе `token` |
| `GET /api/auth/me` | любой вошедший | Текущий пользователь: `id`, `email`, `role`, `driverId` |
| `GET /api/health` | все | Проверка, что сервис жив |
| `POST /api/orders` | `CLIENT` | Создать заказ (ответ `201`): `fromAddress`, `toAddress`, `price` |
| `GET /api/orders/my` | `CLIENT` | Мои заказы |
| `PATCH /api/orders/{id}/cancel` | `CLIENT`, владелец | Отменить заказ (только `NEW`) |
| `GET /api/orders/assigned` | `DRIVER` | Заказы, назначенные мне |
| `PATCH /api/orders/{id}/status` | `DRIVER`, назначенный | Следующий статус: `{"status": "IN_TRANSIT"}` |

Запросы, кроме открытых, требуют заголовок `Authorization: Bearer <token>`.

| Код | Когда |
|---|---|
| 400 | Не прошла валидация, битый JSON, недопустимый переход статуса, email уже занят |
| 401 | Нет токена, токен неверный или истёк, неверный пароль |
| 403 | Не та роль, чужой заказ |
| 404 | Заказ не найден |
| 500 | Kafka недоступна дольше 5 секунд (см. ограничения) |
| 503 | driver-service не ответил или отказал при регистрации водителя |

## Запуск с нуля

Нужно: **JDK 21** и **Docker Desktop** (запущенный). Примеры команд — для PowerShell на Windows.

1. Склонировать **оба** бэкенд-репозитория в одну папку, рядом друг с другом:
   ```
   git clone https://github.com/KopachYahor/citylogistics-core.git
   git clone https://github.com/KopachYahor/citylogistics-driver-service.git
   ```
2. Поднять инфраструктуру: 2 базы PostgreSQL, Kafka и Kafka UI.
   ```
   cd citylogistics-core
   docker compose up -d
   ```
3. Создать файлы с секретами (в git их нет, без них сервисы не стартуют). В каждом
   репозитории скопировать образец `.env.example` в `.env`:
   ```
   copy .env.example .env
   copy ..\citylogistics-driver-service\.env.example ..\citylogistics-driver-service\.env
   ```
   и вписать значения:
   - `JWT_SECRET` — ключ подписи JWT (только в `.env` core);
   - `INTERNAL_API_KEY` — общий ключ core и driver-service, **одинаковый** в обоих `.env`.

   Сгенерировать случайный ключ в PowerShell:
   ```
   $b = New-Object byte[] 32; [Security.Cryptography.RandomNumberGenerator]::Create().GetBytes($b); [Convert]::ToBase64String($b)
   ```
   (или `openssl rand -base64 32`). Spring сам читает `.env` из папки, откуда запущен
   сервис (`spring.config.import` в `application.yml`). Файл `.env` в `.gitignore`.
   Вместо `.env` можно задать те же значения переменными окружения: они важнее файла.
4. Запустить core (порт 8080). Flyway сам применит миграции:
   ```
   .\mvnw.cmd spring-boot:run
   ```
   Или из IntelliJ IDEA: запустить `LogisticsPlatformApplication` (рабочая папка — корень репозитория).
5. В **другом** терминале запустить driver-service (порт 8082):
   ```
   cd ..\citylogistics-driver-service
   .\mvnw.cmd spring-boot:run
   ```
6. Проверить: `http://localhost:8080/api/health` отвечает `{"status":"UP",...}`.
   Сообщения в Kafka видно в Kafka UI: `http://localhost:8090`.

На macOS/Linux: `./mvnw` вместо `.\mvnw.cmd` и `cp` вместо `copy`.

Если при старте core видите `WeakKeyException`, значит `JWT_SECRET` в `.env` пустой или слишком
короткий: нужна base64-строка от 32 байт.

Если регистрация водителя возвращает `503`, проверьте, что driver-service запущен и что
`INTERNAL_API_KEY` в обоих `.env` **одинаковый**: при разных ключах driver-service
отвечает `401`, а core показывает это как `503` (настоящая причина видна в логе core).

Остановить инфраструктуру: `docker compose down`. Данные баз сохранятся в volume.
Чтобы начать с пустыми базами: `docker compose down -v` (**удалит все данные**).

### Быстрая проверка через curl

(Git Bash; в PowerShell пишите `curl.exe` вместо `curl`.)

```bash
# клиент и водитель
curl -X POST localhost:8080/api/auth/register -H "Content-Type: application/json" \
  -d '{"name":"Client","email":"client@test.by","password":"secret1","role":"CLIENT"}'
curl -X POST localhost:8080/api/auth/register -H "Content-Type: application/json" \
  -d '{"name":"Driver","email":"driver@test.by","password":"secret1","phone":"+375291112233","role":"DRIVER"}'

# вход: скопируйте token из ответа
curl -X POST localhost:8080/api/auth/login -H "Content-Type: application/json" \
  -d '{"email":"client@test.by","password":"secret1"}'

# заказ: через секунду он станет ACCEPTED с driverId
curl -X POST localhost:8080/api/orders -H "Authorization: Bearer <token>" \
  -H "Content-Type: application/json" -d '{"fromAddress":"Minsk","toAddress":"Grodno","price":300}'
curl localhost:8080/api/orders/my -H "Authorization: Bearer <token>"
```

## Тесты

```
.\mvnw.cmd test
```

Нужен запущенный Docker. Тесты сами поднимают временную PostgreSQL (Testcontainers),
поэтому **не трогают** базу из `docker-compose`, а Flyway каждый раз применяет все
миграции на пустую базу. Kafka, `JWT_SECRET` и `INTERNAL_API_KEY` для тестов не нужны.

| Вид | Классы | Что проверяют |
|---|---|---|
| Unit (Mockito) | `OrderServiceTest`, `DriverAssignedListenerTest`, `OrderStatusTest` | Бизнес-правила: переходы статусов, владелец и водитель заказа, обработка `driver.assigned` |
| API (MockMvc) | `AuthControllerTest`, `OrderControllerSecurityTest`, `HealthControllerTest` | Коды ответов, валидация, 401/403, права по ролям |
| Интеграционный (Testcontainers) | `OrderRepositoryTest` | Репозиторий на настоящей PostgreSQL |

## Ключевые решения

- **Конечный автомат статусов.** `OrderStatus.canTransitionTo` разрешает только следующий
  шаг: нельзя перескочить `ACCEPTED → DELIVERED` или вернуться назад.
- **Права проверяет бэкенд.** Роли — в `SecurityConfig` (`hasRole`), владелец заказа и
  назначенный водитель — в `OrderService`. Скрытые кнопки на фронтенде — не защита.
- **JWT без состояния.** В токене только email (`sub`) и срок жизни. Роль при каждом
  запросе берётся из базы (`CustomUserDetailsService`), поэтому смена роли действует сразу.
- **Пессимистическая блокировка.** Отмена заказа, смена статуса и назначение водителя
  читают заказ через `SELECT ... FOR UPDATE` внутри транзакции. Иначе одновременные
  отмена и назначение затирали бы друг друга (lost update).
- **Слушатели не доверяют событиям вслепую.** Kafka доставляет «хотя бы один раз», события
  приходят с задержкой. Поэтому `DriverAssignedListener` проверяет текущий статус заказа.
- **Компенсирующее действие при регистрации водителя.** Если driver-service не ответил
  (таймаут 2 с на соединение, 5 с на ответ), пользователь удаляется, клиент получает 503.
  Это ручной откат, а не полноценная Saga (см. ограничения).
- **Сущности наружу не отдаются.** Ответы — DTO (`OrderResponse`, `UserResponse`).
- **Сервис-сервис по общему ключу.** driver-service отвечает на REST только с правильным
  `X-Internal-Api-Key`, ключ приходит из переменной окружения `INTERNAL_API_KEY`.
- **Email без учёта регистра.** При регистрации и входе email приводится к нижнему регистру,
  а одновременная регистрация с одним email упирается в `UNIQUE` и даёт `400`, а не `500`.

## Известные ограничения

Это учебный проект. Ниже то, что сознательно **не** сделано, и чем это грозит.

1. **Нет Outbox (проблема двойной записи).** Запись в БД и отправка в Kafka — две
   отдельные операции, общей транзакции у них нет.
   - **Создание заказа:** заказ сохраняется, потом отправляется `order.created`. Если Kafka
     недоступна, `send()` через 5 секунд (`max.block.ms`) бросает ошибку: клиент получает
     `500`, хотя заказ уже сохранён со статусом `NEW` и так в нём и останется.
   - **Отмена и смена статуса:** отправка идёт внутри транзакции. Если Kafka недоступна,
     транзакция откатывается и данные не расходятся. Но событие уходит в Kafka до коммита:
     если упадёт сам коммит, driver-service получит событие о том, чего в базе нет.
   - Если брокер принял соединение, но потерял сообщение, это никто не заметит:
     результат отправки асинхронный и не проверяется.

   Решение — паттерн Transactional Outbox.
2. **Нет повторной попытки назначения.** Если свободных водителей нет, driver-service
   пишет предупреждение в лог, и заказ остаётся `NEW`. Когда водитель освобождается,
   ожидающие заказы никто не перепроверяет.
3. **Откат при регистрации водителя не гарантирует согласованность.** Если driver-service
   создал водителя, но ответил позже таймаута, core удалит пользователя, а водитель
   в driver-service останется «сиротой». И наоборот: если core упадёт между сохранением
   пользователя и ответом driver-service, откат не выполнится, и в core останется
   пользователь-водитель без `driverId`.
4. **Защита driver-service простая.** Один общий ключ на всех: у него нет срока жизни и
   ролей, при утечке его нужно сменить вручную в обоих сервисах. Для учебного проекта
   хватает, в продакшене обычно используют токены сервисов (например, OAuth2 client
   credentials) или mTLS.
5. **DTO событий продублированы в обоих сервисах с одинаковыми пакетами.**
   `JsonSerializer` пишет полное имя класса в заголовок `__TypeId__`, и получатель ищет
   класс с тем же именем. Поэтому, например, `DriverAssignedEvent` в core лежит в папке
   `order/dto`, но объявлен в пакете `com.kopach.logistics.driver.dto`. Переименование
   пакета в одном сервисе ломает чтение событий в другом.
6. **JWT нельзя отозвать.** Токен действует 24 часа. Выхода из системы на сервере нет.
7. **CORS разрешён только для `http://localhost:5500` и `http://127.0.0.1:5500`** (в коде).
8. **Пароли баз в `docker-compose.yml`** — это значения только для локальной разработки.
9. **История миграций.** V3 создаёт таблицу `order_status_history`, которая так и не
   понадобилась, V8 её удаляет. V4/V5 правились уже после применения (проект нигде
   не развёрнут). Правило на будущее: применённую миграцию не меняют, пишут новую.
