# CityLogistics — Core Service

Учебный пет-проект: система доставки по городу. Клиент создаёт заказ, система сама
назначает свободного водителя, водитель проводит заказ по статусам до доставки.

Проект состоит из трёх репозиториев:

| Репозиторий | Что это | Порт |
|---|---|---|
| **citylogistics-core** (этот) | Пользователи, регистрация, заказы | 8080, БД `logistics` на 5432 |
| [citylogistics-driver-service](https://github.com/KopachYahor/citylogistics-driver-service) | Водители и их статусы | 8082, БД `drivers` на 5433 |
| [citylogistics-frontend](https://github.com/KopachYahor/citylogistics-frontend) | HTML/CSS/Vanilla JS | 5500 |

Инфраструктура для всех сервисов описана в `docker-compose.yml` этого репозитория.
Вход и пароли — в **Keycloak** (порт 8180), он тоже поднимается из этого `docker-compose.yml`.

## Стек

Java 21, Spring Boot 3.3 (Web, Data JPA, Security, Validation, Kafka), PostgreSQL 15,
Flyway, Keycloak 26 (OAuth2 / OpenID Connect, Spring Security OAuth2 Resource Server), Apache Kafka (KRaft, без ZooKeeper), Docker Compose,
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
- Фронтенд общается с core по REST, а входит через Keycloak. Про Kafka он не знает.
- Сервисы общаются через Kafka асинхронно. Синхронный REST-вызов один: при регистрации
  водителя core создаёт его профиль в driver-service. REST driver-service закрыт общим
  ключом: core передаёт его в заголовке `X-Internal-Api-Key`.

### Вход через Keycloak

```
 Браузер ──1. «Войти»──▶ Keycloak (8180): страница входа, пароль вводится здесь
    ▲                         │
    └──2. редирект с code─────┘
 Браузер ──3. code + code_verifier──▶ Keycloak ──▶ access token (5 мин) + refresh token
 Браузер ──4. Authorization: Bearer <access token>──▶ core: проверка подписи открытым ключом Keycloak
```

- **Authorization Code + PKCE** (фронтенд, `js/auth.js`). Браузер — публичный клиент без
  секрета, поэтому перехваченный `code` без исходного `code_verifier` бесполезен.
- **core — OAuth2 Resource Server.** Токены выдаёт Keycloak, core только проверяет их: подпись
  (RS256, открытые ключи берутся по `jwk-set-uri`), срок и издателя (`iss`). Общего секрета
  для подписи, как было со своим JWT, больше нет.
- **Роли** — роли realm `CLIENT` и `DRIVER`. Keycloak кладёт их в `realm_access.roles`,
  `KeycloakJwtConverter` превращает их в `ROLE_CLIENT` / `ROLE_DRIVER`, а пользователя
  приложения (`User`) находит по `sub` токена (колонка `users.keycloak_id`).
- **Регистрация остаётся в core** (`POST /api/auth/register`): нужно выбрать роль и для
  водителя создать профиль в driver-service. core создаёт пользователя в Keycloak через
  Admin API (служебный клиент `citylogistics-core`, client credentials), затем своего `User`,
  затем водителя. Если шаг падает, уже созданное удаляется.
- Настройки realm (роли, клиенты, профиль пользователя) — в `keycloak/realm-export.json`,
  Keycloak импортирует его при первом старте.
- **Адреса Keycloak.** Браузер ходит на `localhost:8180`, core из Docker — на `keycloak:8080`.
  Чтобы `iss` в токенах был одинаковым, у Keycloak задан `KC_HOSTNAME=http://localhost:8180`,
  а core проверяет `iss` по `KEYCLOAK_ISSUER_URI`, ключи же скачивает по `KEYCLOAK_URL`.

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
| `POST /api/auth/register` | все | Регистрация (ответ `201`): пользователь в Keycloak, в core и, для водителя, в driver-service. Тело: `name`, `email`, `password`, `role` (`CLIENT` / `DRIVER`), `phone` (обязателен для `DRIVER`). Email хранится в нижнем регистре. Входа в core нет: токен выдаёт Keycloak |
| `GET /api/auth/me` | любой вошедший | Текущий пользователь: `id`, `email`, `role`, `driverId` |
| `GET /actuator/health` | все | Spring Boot Actuator: `UP`, если приложение и БД доступны, иначе `DOWN` и 503 |
| `POST /api/orders` | `CLIENT` | Создать заказ (ответ `201`): `fromAddress`, `toAddress`, `price` |
| `GET /api/orders/my` | `CLIENT` | Мои заказы |
| `PATCH /api/orders/{id}/cancel` | `CLIENT`, владелец | Отменить заказ (только `NEW`) |
| `GET /api/orders/assigned` | `DRIVER` | Заказы, назначенные мне |
| `PATCH /api/orders/{id}/status` | `DRIVER`, назначенный | Следующий статус: `{"status": "IN_TRANSIT"}` |

Запросы, кроме открытых, требуют заголовок `Authorization: Bearer <access token Keycloak>`.

| Код | Когда |
|---|---|
| 400 | Не прошла валидация, битый JSON, недопустимый переход статуса, email уже занят |
| 401 | Нет токена, токен неверный или истёк, пользователь есть в Keycloak, но не зарегистрирован в приложении |
| 403 | Не та роль, чужой заказ |
| 404 | Заказ не найден |
| 503 | Keycloak или driver-service не ответил при регистрации |

## Запуск с нуля

Нужен только **Docker Desktop** (запущенный). Примеры команд — для PowerShell на Windows.

1. Склонировать **оба** бэкенд-репозитория в одну папку, рядом друг с другом
   (compose собирает driver-service из соседней папки `../citylogistics-driver-service`):
   ```
   git clone https://github.com/KopachYahor/citylogistics-core.git
   git clone https://github.com/KopachYahor/citylogistics-driver-service.git
   cd citylogistics-core
   ```
2. Создать файл с секретами (в git его нет, без него сервисы не стартуют):
   ```
   copy .env.example .env
   ```
   и вписать значения:
   - `INTERNAL_API_KEY` — общий ключ core и driver-service;
   - `KEYCLOAK_CORE_CLIENT_SECRET` — секрет служебного клиента core в Keycloak;
   - `KEYCLOAK_ADMIN_PASSWORD` — пароль `admin` для консоли Keycloak (пусто — будет `admin`).

   Сгенерировать случайный ключ в PowerShell:
   ```
   $b = New-Object byte[] 32; [Security.Cryptography.RandomNumberGenerator]::Create().GetBytes($b); [Convert]::ToHexString($b)
   ```
   (или `openssl rand -hex 32`). Docker Compose сам читает `.env` рядом с `docker-compose.yml`
   и передаёт ключи сервисам и Keycloak, поэтому значения у всех гарантированно одинаковые.
3. Собрать образы сервисов и поднять всё: 2 базы PostgreSQL, Kafka, Kafka UI, Keycloak, core и driver-service.
   ```
   docker compose up -d --build
   ```
   Первая сборка идёт несколько минут: скачиваются образы и зависимости Maven. Сервисы стартуют
   только после того, как базы и Kafka станут готовы (`depends_on` + `healthcheck`).
   Flyway сам применит миграции.
4. Проверить: `docker compose ps` показывает `healthy` у всех, кроме Kafka UI;
   `http://localhost:8080/actuator/health` отвечает `{"status":"UP"}`.
   Консоль Keycloak: `http://localhost:8180` (логин `admin`), realm `citylogistics`.
   Сообщения в Kafka видно в Kafka UI: `http://localhost:8090`. Логи сервиса: `docker compose logs -f core`.

### Как устроен запуск в Docker

- **Dockerfile** в каждом сервисе двухэтапный: на первом этапе образ с Maven собирает jar
  (тесты при этом не запускаются, им нужен Docker), на втором jar кладётся в лёгкий образ
  только с JRE и запускается не от root.
- **Адреса внутри Docker другие.** В контейнере `localhost` — это сам контейнер, а соседи доступны
  по имени сервиса: `postgres:5432`, `kafka:9092`, `driver-service:8082`, `keycloak:8080`. В `application.yml`
  остаются адреса для запуска из IDE (`localhost`), а в `docker-compose.yml` они заменяются
  переменными окружения (`SPRING_DATASOURCE_URL` и т.п.): переменные окружения важнее `application.yml`.
- **У Kafka два listener'а:** `kafka:9092` для контейнеров и `localhost:29092` для приложений на хосте.

### Разработка: сервисы из IDE, инфраструктура в Docker

Нужен ещё **JDK 21**.

1. Поднять только инфраструктуру:
   ```
   docker compose up -d postgres postgres-drivers kafka kafka-ui keycloak
   ```
   Если перед этим всё было запущено целиком, сначала остановите контейнеры сервисов, иначе
   порты 8080 и 8082 будут заняты: `docker compose stop core driver-service`.
2. Скопировать `.env.example` в `.env` и в репозитории driver-service, вписать тот же
   `INTERNAL_API_KEY`. core из IDE берёт из своего `.env` и `KEYCLOAK_CORE_CLIENT_SECRET`
   (тот же, что получил Keycloak), а Keycloak ищет на `localhost:8180`. Spring сам читает `.env` из папки, откуда запущен сервис
   (`spring.config.import` в `application.yml`).
3. Запустить core (порт 8080): `.\mvnw.cmd spring-boot:run` или `LogisticsPlatformApplication`
   в IntelliJ IDEA (рабочая папка — корень репозитория).
4. В **другом** терминале запустить driver-service (порт 8082):
   ```
   cd ..\citylogistics-driver-service
   .\mvnw.cmd spring-boot:run
   ```

На macOS/Linux: `./mvnw` вместо `.\mvnw.cmd` и `cp` вместо `copy`.

Если регистрация возвращает `503`, а в логе core ошибка `401` от Keycloak, значит
`KEYCLOAK_CORE_CLIENT_SECRET` в `.env` не совпадает с секретом клиента в Keycloak. Keycloak
берёт секрет только при **первом** импорте realm: после смены секрета пересоздайте Keycloak
(`docker compose rm -sf keycloak` и `docker volume rm citylogistics-core_keycloak_data`).

Если регистрация водителя возвращает `503`, проверьте, что driver-service запущен и что
`INTERNAL_API_KEY` у core и driver-service **одинаковый**: при разных ключах driver-service
отвечает `401`, а core показывает это как `503` (настоящая причина видна в логе core).

Остановить всё: `docker compose down`. Данные баз и пользователи Keycloak сохранятся в volume.
Чтобы начать с пустыми базами: `docker compose down -v` (**удалит все данные**, в том числе
пользователей Keycloak; realm заново импортируется при следующем старте).

**Переход со старой версии (свой JWT).** Миграция V9 удаляет `users.password_hash`: старые
пользователи войти не смогут, их нет в Keycloak. Для локальной разработки проще всего
начать с чистых баз: `docker compose down -v`.

### Быстрая проверка через curl

(Git Bash; в PowerShell пишите `curl.exe` вместо `curl`.)

```bash
# клиент и водитель
curl -X POST localhost:8080/api/auth/register -H "Content-Type: application/json" \
  -d '{"name":"Client","email":"client@test.by","password":"secret1","role":"CLIENT"}'
curl -X POST localhost:8080/api/auth/register -H "Content-Type: application/json" \
  -d '{"name":"Driver","email":"driver@test.by","password":"secret1","phone":"+375291112233","role":"DRIVER"}'

# вход — через страницу Keycloak во фронтенде. Токен для curl можно взять там:
# в консоли браузера localStorage.getItem('token') (живёт 5 минут)

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
миграции на пустую базу. Kafka, Keycloak и секреты для тестов не нужны: `JwtDecoder` и
`KeycloakAdminClient` подменяются моками (`@MockBean`). Подменённый `JwtDecoder` по строке
токена возвращает готовый `Jwt` (`TestJwts`), поэтому `KeycloakJwtConverter` и правила
`SecurityConfig` в тестах работают по-настоящему.

| Вид | Классы | Что проверяют |
|---|---|---|
| Unit (Mockito) | `OrderServiceTest`, `DriverAssignedListenerTest`, `OrderStatusTest`, `KeycloakJwtConverterTest` | Бизнес-правила: переходы статусов, владелец и водитель заказа, обработка `driver.assigned`, роли из токена Keycloak |
| API (MockMvc) | `AuthControllerTest`, `OrderControllerSecurityTest`, `HealthEndpointTest` | Коды ответов, валидация, 401/403, права по ролям, регистрация через Keycloak и её откат |
| Интеграционный (Testcontainers) | `OrderRepositoryTest` | Репозиторий на настоящей PostgreSQL |

## Ключевые решения

- **Конечный автомат статусов.** `OrderStatus.canTransitionTo` разрешает только следующий
  шаг: нельзя перескочить `ACCEPTED → DELIVERED` или вернуться назад.
- **Права проверяет бэкенд.** Роли — в `SecurityConfig` (`hasRole`), владелец заказа и
  назначенный водитель — в `OrderService`. Скрытые кнопки на фронтенде — не защита.
- **Аутентификация вынесена в Keycloak.** core не хранит пароли и не выдаёт токены, а
  только проверяет их (OAuth2 Resource Server). Роли берутся из токена, пользователь
  приложения — из базы по `sub`. Keycloak даёт и то, чего не было в своём JWT: короткие
  токены с обновлением, выход с завершением сессии, защиту от перебора паролей (`bruteForceProtected`).
- **Пессимистическая блокировка.** Отмена заказа, смена статуса и назначение водителя
  читают заказ через `SELECT ... FOR UPDATE` внутри транзакции. Иначе одновременные
  отмена и назначение затирали бы друг друга (lost update).
- **Слушатели не доверяют событиям вслепую.** Kafka доставляет «хотя бы один раз», события
  приходят с задержкой. Поэтому `DriverAssignedListener` проверяет текущий статус заказа.
  Если заказа нет вовсе (например, база пересоздана, а событие осталось в Kafka), он не бросает
  исключение, а публикует отмену с этим водителем, и driver-service освобождает водителя.
- **Событие — только после коммита.** Внутри транзакции `OrderEventPublisher` откладывает
  отправку в Kafka до успешного коммита (`TransactionSynchronization.afterCommit`): другой сервис
  не узнает об изменении, которого нет в базе. Ошибка отправки пишется в лог и не ломает запрос:
  данные уже сохранены.
- **Повтор назначения.** `UnassignedOrderRetryScheduler` раз в 30 секунд заново публикует
  `order.created` для заказов, которые дольше 30 секунд в `NEW` (сначала самые старые). Так заказ
  получает водителя, когда тот освободится, и восстанавливается после потерянного события.
  Повтор безопасен: driver-service не создаёт второе назначение, а уже сделанное отправляет
  ещё раз. Настройки — `orders.assignment-retry` в `application.yml`.
- **Компенсирующие действия при регистрации.** Порядок: Keycloak → `User` в core →
  водитель в driver-service. Если driver-service не ответил (таймаут 2 с на соединение,
  5 с на ответ), удаляются и `User`, и пользователь в Keycloak, клиент получает 503.
  Это ручной откат, а не полноценная Saga (см. ограничения).
- **Сущности наружу не отдаются.** Ответы — DTO (`OrderResponse`, `UserResponse`).
- **Сервис-сервис по общему ключу.** driver-service отвечает на REST только с правильным
  `X-Internal-Api-Key`, ключ приходит из переменной окружения `INTERNAL_API_KEY`.
- **Email без учёта регистра.** При регистрации и входе email приводится к нижнему регистру,
  а одновременная регистрация с одним email упирается в `UNIQUE` и даёт `400`, а не `500`.

## Известные ограничения

Это учебный проект. Ниже то, что сознательно **не** сделано, и чем это грозит.

1. **Нет Outbox (проблема двойной записи).** Запись в БД и отправка в Kafka — две
   отдельные операции, общей транзакции у них нет. Событие отправляется после коммита,
   поэтому «событий о несуществующем» больше нет, но обратная ситуация осталась: если Kafka
   недоступна сразу после коммита, событие теряется (ошибка видна только в логе).
   - `order.created` восстанавливает повтор назначения: заказ в `NEW` опубликуется снова.
   - `order.status-changed` не восстанавливается: если потеряется `DELIVERED`, водитель
     останется `BUSY`.

   Полное решение — паттерн Transactional Outbox.
2. **Повтор назначения — опрос раз в 30 секунд.** Освободившийся водитель получает ожидающий
   заказ не сразу, а при следующем запуске планировщика. Пока свободных водителей нет, заказы
   в `NEW` публикуются каждые 30 секунд (до 50 за раз), и driver-service пишет об этом в лог.
   При нескольких экземплярах core повтор отправит каждый из них — это безопасно, но лишне.
3. **Откат при регистрации не гарантирует согласованность.** Участников три: Keycloak, core,
   driver-service. Если driver-service создал водителя, но ответил позже таймаута, core удалит
   пользователя, а водитель в driver-service останется «сиротой» и будет получать заказы.
   Если core упадёт посреди регистрации, откат не выполнится вовсе: например, в Keycloak
   останется пользователь без `User` в core — войти он сможет, но API ответит `401`.
   Если не удастся удалить пользователя в Keycloak, это видно только в логе core.
4. **Защита driver-service простая.** Один общий ключ на всех: у него нет срока жизни и
   ролей, при утечке его нужно сменить вручную в обоих сервисах. Для учебного проекта
   хватает, в продакшене обычно используют токены сервисов (например, OAuth2 client
   credentials) или mTLS.
5. **DTO событий продублированы в обоих сервисах с одинаковыми пакетами.**
   `JsonSerializer` пишет полное имя класса в заголовок `__TypeId__`, и получатель ищет
   класс с тем же именем. Поэтому, например, `DriverAssignedEvent` в core лежит в папке
   `order/dto`, но объявлен в пакете `com.kopach.logistics.driver.dto`. Переименование
   пакета в одном сервисе ломает чтение событий в другом.
6. **Access token нельзя отозвать.** После выхода уже выданный access token действует до
   конца своего срока (5 минут): core проверяет токен сам, без запроса в Keycloak. Refresh
   token и сессия Keycloak при выходе завершаются.
7. **Keycloak в режиме разработки** (`start-dev`): HTTP, встроенная база H2 в volume,
   пароль администратора по умолчанию `admin`. Для продакшена — `start` с HTTPS и PostgreSQL.
   Аудиторию токена (`aud`) core не проверяет.
8. **CORS разрешён только для `http://localhost:5500` и `http://127.0.0.1:5500`** (в коде).
9. **Пароли баз в `docker-compose.yml`** — это значения только для локальной разработки.
10. **История миграций.** V3 создаёт таблицу `order_status_history`, которая так и не
   понадобилась, V8 её удаляет. V4/V5 правились уже после применения (проект нигде
   не развёрнут). Правило на будущее: применённую миграцию не меняют, пишут новую.
