# Система службы доставки

Сквозной учебный проект по дисциплине «Программирование корпоративных систем»:
одна информационная система службы доставки, которая развивается от КР №1 к КР №4.

| Работа | Что сделано | Каталог |
|---|---|---|
| КР №1 | Консольное приложение на Java + PostgreSQL (JDBC) | `src/`, `database/` |
| КР №2 | Настольный JavaFX-клиент к базе КР №1 (JDBC) | `javafx-client/` |
| КР №3 | Spring Boot REST API + web-интерфейс, PostgreSQL через Docker | `backend/` |
| КР №4 | Android-клиент к REST API из КР №3 | — |

## Структура репозитория

```
delivery/
├── README.md
├── .env.example                 шаблон настроек (КР №1–3); реальный .env не коммитится
├── pom.xml                      Maven-проект консольного приложения (КР №1)
├── src/                         КР №1: Console UI → Service → Repository (JDBC) → PostgreSQL
├── database/                    КР №1: schema.sql, seed.sql, docker-compose.yml (PostgreSQL, порт 55432)
├── docs/ER-DIAGRAM.md           ER-диаграмма базы КР №1
├── javafx-client/               КР №2: JavaFX-приложение (см. javafx-client/README.md)
└── backend/                     КР №3: Spring Boot backend + web UI
    ├── Dockerfile               двухэтапная сборка: Maven → JRE 21
    ├── docker-compose.yml       PostgreSQL 17 + backend, healthcheck'и, volume для данных
    ├── start-compose.ps1        запуск (из корня репозитория)
    ├── stop-compose.ps1         остановка без удаления данных
    ├── pom.xml
    └── src/
        ├── main/java/org/example/delivery/
        │   ├── DeliveryApplication.java   точка входа Spring Boot
        │   ├── config/        Spring Security, OpenAPI (Swagger), параметры app.*, создание первого администратора
        │   ├── controller/    REST-контроллеры: заказы, клиенты, комментарии, auth, пользователи, аудит, аналитика
        │   ├── service/       бизнес-логика и транзакции, расчёт приоритета, CSV, аудит, вход/refresh
        │   ├── repository/    Spring Data JPA
        │   ├── domain/        JPA-сущности и enum
        │   ├── dto/           входные и выходные модели API (record)
        │   ├── filter/        разбор умного фильтра → JPA Criteria (без конкатенации SQL)
        │   ├── security/      JWT, фильтры аутентификации, текущий пользователь, политика паролей
        │   ├── error/         единый JSON-формат ошибок
        │   └── web/           Correlation ID, безопасная пагинация и сортировка
        ├── main/resources/
        │   ├── application.yml
        │   ├── application-test.yml       профиль тестов (H2 в памяти)
        │   ├── db/migration/              миграции Flyway V1–V3
        │   └── static/                    web-интерфейс: index.html, app.js, styles.css, страницы ошибок
        └── test/java/...                  тесты
```

## Предметная область

Основные сущности:

- `Client` — клиент службы доставки (справочник);
- `DeliveryOrder` — заказ на доставку (основная сущность);
- `DeliveryStatus` — статус заказа.

Статусы и допустимые переходы:

```
CREATED → CONFIRMED → ASSIGNED_TO_COURIER → PICKED_UP → IN_TRANSIT → DELIVERED
   └──────────┴───────────────┴──→ CANCELLED
```

Бизнес-правила:

1. клиент должен существовать при создании заказа (в КР №3 — ещё и быть активным);
2. адреса отправления и доставки обязательны и не могут совпадать;
3. вес и стоимость должны быть больше нуля;
4. статус меняется только по разрешённой последовательности;
5. отменить заказ можно только до того, как курьер его забрал; доставленный заказ изменить нельзя.

---

## КР №1. Консольное приложение

- CRUD клиентов и заказов, поиск, фильтрация по статусу и стоимости, сортировка;
- последовательная смена статусов, статистика, экспорт в Excel `.xlsx`;
- PostgreSQL через JDBC и параметризованные `PreparedStatement`;
- слои `Console UI → Service → Repository/JDBC → PostgreSQL`.

### База данных

Вариант 1 — Docker (нужен Docker Desktop), из папки `database/`:

```bash
docker compose up -d --wait
```

- PostgreSQL на порту **55432**, база `delivery_db`, пользователь и пароль `postgres`;
- `schema.sql` и `seed.sql` применяются автоматически при первом запуске пустого контейнера;
- данные хранятся в volume `delivery-pgdata`, поэтому `docker compose down` их не удаляет.

Вариант 2 — локальный PostgreSQL:

```bash
psql -U postgres -c "CREATE DATABASE delivery_db;"
psql -U postgres -d delivery_db -v ON_ERROR_STOP=1 -f database/schema.sql
psql -U postgres -d delivery_db -v ON_ERROR_STOP=1 -f database/seed.sql
```

`database/seed.sql` добавляет 5 клиентов и 10 заказов в 7 разных статусах.

### Запуск

Параметры подключения `DB_URL`, `DB_USER`, `DB_PASSWORD` читаются из переменных окружения,
Java-свойств или файла `.env` в корне (в таком порядке приоритета).

```bash
mvn clean test
mvn exec:java          # или запустить org.example.delivery.Main
```

---

## КР №2. JavaFX-клиент

Настольное приложение в `javafx-client/`, работает с той же базой КР №1 через JDBC.
Подробности — в [`javafx-client/README.md`](javafx-client/README.md).

```bash
cd javafx-client
mvn clean javafx:run   # или .\run.ps1
```

---

## КР №3. Backend (Spring Boot REST API) и web-интерфейс

Spring Boot 3.5, Java 21, PostgreSQL 17, Flyway, Spring Security + JWT, Swagger, Docker Compose.
Web-интерфейс написан на HTML/CSS/JS без отдельной сборки и раздаётся самим backend.
Тот же REST API будет использовать Android-приложение КР №4 (из эмулятора: `http://10.0.2.2:8080/api`).

### Перенос с примера Student Hub

| Student Hub (пример) | Служба доставки |
|---|---|
| `Student` | `DeliveryOrder` — заказ (`/api/orders`) |
| `StudyGroup` | `Client` — клиент, справочник (`/api/clients`) |
| `StudentStatus` | `DeliveryStatus` — статусы КР №1 с правилами переходов |
| `RiskLevel` | `PriorityLevel` — вычисляемый приоритет заказа |
| `StudentNote` | `OrderNote` — комментарии к заказу |
| `student_tag` | `order_tag` — теги заказа |
| роли VIEWER / TEACHER / ADMIN | `VIEWER` (просмотр), `DISPATCHER` (редактирование), `ADMIN` (администрирование) |

Поля заказа:
- UUID и номер `order_number` (внешний идентификатор, например `DLV-000001`);
- клиент, адреса, описание;
- вес и стоимость (числовые показатели), `fragile` — хрупкий груз (логический признак);
- статус, теги, вычисляемый приоритет;
- `created_at` / `updated_at`, `version`, признак архива.

**Приоритет** (`PriorityCalculator`):
- хрупкий груз: +2;
- цена ≥ 1500 ₽: +2, цена ≥ 800 ₽: +1;
- вес ≥ 5 кг: +1;
- посылка у курьера (`PICKED_UP`, `IN_TRANSIT`): +1.

Итог: ≥ 4 — `URGENT`, 3 — `HIGH`, 1–2 — `NORMAL`, 0 — `LOW`.
Для `DELIVERED` и `CANCELLED` — `NOT_APPLICABLE`.

### База данных и Flyway

Схема создаётся и изменяется только миграциями; Hibernate работает в режиме `ddl-auto: validate`.

- `V1__kr1_initial_schema.sql` — схема КР №1 без изменений;
- `V2__kr1_seed_data.sql` — данные КР №1;
- `V3__backend_schema.sql` — развитие под backend:
  - переход `clients` и `delivery_orders` с BIGSERIAL на UUID с сохранением данных;
  - новые поля заказа;
  - пользователи и роли, refresh-сессии, теги, комментарии, журнал аудита, индексы.

### Запуск

Нужен Docker Desktop. Файл `.env` лежит в корне репозитория (шаблон — `.env.example`;
в нём нужно задать пароль БД, JWT-секрет и временный пароль администратора).

```powershell
powershell -ExecutionPolicy Bypass -File .\backend\start-compose.ps1
# или
docker compose --env-file .env -f backend/docker-compose.yml up -d --build --wait
```

| Назначение | Адрес |
|---|---|
| Web-интерфейс | http://localhost:8080/ |
| Swagger UI | http://localhost:8080/swagger-ui.html (только ADMIN) |
| REST API | http://localhost:8080/api |
| OpenAPI JSON | http://localhost:8080/v3/api-docs |
| Health | http://localhost:8080/actuator/health |
| PostgreSQL | `jdbc:postgresql://localhost:55433/delivery_kr3`, пользователь `delivery_app` |

PostgreSQL КР №3 открыт на порту 55433, чтобы не конфликтовать с базой КР №1/2 на 55432.

Первый вход: `admin@example.org` и временный пароль `BOOTSTRAP_ADMIN_PASSWORD` из `.env`.
После входа система потребует сменить его на постоянный. Пароль должен быть не короче 12 символов,
содержать буквы обоих регистров, цифру и спецсимвол и не включать логин.

Остановка с сохранением данных: `.\backend\stop-compose.ps1`.
Полный сброс базы: `docker compose --env-file .env -f backend/docker-compose.yml down -v`.

### Роли

| Роль | Права |
|---|---|
| `VIEWER` | просмотр заказов, клиентов, комментариев, аналитики, экспорт CSV |
| `DISPATCHER` | плюс создание и изменение заказов, архив, массовые операции, импорт CSV, комментарии |
| `ADMIN` | плюс справочник клиентов, пользователи, журнал аудита, Swagger, окончательное удаление |

### REST API

| Назначение | Метод и адрес |
|---|---|
| Вход / refresh / выход | `POST /api/auth/login`, `POST /api/auth/refresh`, `POST /api/auth/logout` |
| Текущий пользователь | `GET /api/auth/me` |
| Смена пароля / первичная смена | `POST /api/auth/change-password`, `POST /api/auth/change-initial-password` |
| Список и карточка заказа | `GET /api/orders`, `GET /api/orders/{id}` |
| Создание и изменение | `POST /api/orders`, `PUT /api/orders/{id}` |
| Архив и восстановление | `DELETE /api/orders/{id}`, `POST /api/orders/{id}/restore` |
| Окончательное удаление (ADMIN) | `DELETE /api/orders/{id}/permanent` + заголовок `X-Confirm-Delete: PERMANENTLY_DELETE` |
| Массовые операции | `POST /api/orders/bulk` — `ARCHIVE`, `RESTORE`, `SET_STATUS`, `ADD_TAG`, `REMOVE_TAG` |
| CSV | `POST /api/orders/import/preview`, `POST /api/orders/import/apply`, `GET /api/orders/export?q=...` |
| Комментарии | `GET`/`POST /api/orders/{id}/notes`, `PUT`/`DELETE /api/orders/{id}/notes/{noteId}` |
| Клиенты | `GET /api/clients`, `POST /api/clients`, `PUT /api/clients/{id}`, `PATCH /api/clients/{id}/active` |
| Аналитика | `GET /api/analytics/summary` |
| Пользователи (ADMIN) | `GET`/`POST /api/admin/users`, `PUT /api/admin/users/{id}`, `POST /api/admin/users/{id}/reset-password` |
| Аудит (ADMIN) | `GET /api/admin/audit` |

Параметры списка заказов:
- `q` — умный фильтр;
- `page`, `size` (1–100);
- `sort` — `created`, `number`, `client`, `status`, `price`, `weight`, `priority`, `updated` плюс `,asc` или `,desc`.

Пример фильтра:

```
Книга клиент:Иванов статус:в_пути хрупкий:да цена>=500 вес<3 приоритет:высокий тег:срочно архив:все
```

Строка разбирается в типизированную модель и превращается в параметризованный JPA Criteria-запрос
без конкатенации SQL.

Ошибки возвращаются в едином JSON-формате:
- 400 — неверные данные;
- 401 — нет авторизации;
- 403 — недостаточно прав;
- 404 — запись не найдена;
- 409 — конфликт версий.

Stack trace клиенту не передаётся.

### CSV

Колонки: `orderNumber, clientPhone, pickupAddress, deliveryAddress, description, weight, price, fragile, status, tags`.
Теги разделяются символом `|`. Файл, полученный экспортом, подходит для импорта.

Режимы импорта:
- `CREATE_ONLY` — только новые заказы;
- `UPSERT_BY_ORDER_NUMBER` — обновление существующих по номеру заказа.

### Тесты

Тесты используют H2 в памяти, база в Docker для них не нужна. Запуск: `mvn -f backend/pom.xml test`.

| Тест | Что проверяет |
|---|---|
| `ContextSmokeTest` | Spring-контекст поднимается целиком |
| `PriorityCalculatorTest` | расчёт приоритета |
| `DeliveryStatusTest` | допустимые переходы статусов |
| `SmartFilterParserTest` | разбор умного фильтра и защита от SQL-инъекций |
| `PasswordPolicyTest` | политика паролей |
| `ApiSecurityTest` | без токена — 401, единый формат ошибок |
