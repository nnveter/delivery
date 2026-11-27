# Система службы доставки

## Возможности

- CRUD клиентов и заказов;
- поиск клиентов по ФИО, телефону и email;
- поиск заказов по клиенту, телефону и адресам;
- фильтрация заказов по статусу и диапазону стоимости;
- сортировка по цене, весу и дате создания;
- последовательная смена статусов заказа;
- статистика системы минимум по шести показателям;
- экспорт клиентов и заказов в Excel `.xlsx`;
- PostgreSQL через JDBC и параметризованные `PreparedStatement`;
- разделение `Console UI -> Service -> Repository/JDBC -> PostgreSQL`.

## Предметная модель

Основные сущности:

- `Client` — клиент службы доставки;
- `DeliveryOrder` — заказ на доставку;
- `DeliveryStatus` — перечисление статусов заказа.

Статусы: `CREATED`, `CONFIRMED`, `ASSIGNED_TO_COURIER`, `PICKED_UP`, `IN_TRANSIT`, `DELIVERED`, `CANCELLED`.

Бизнес-правила реализованы в Java-коде:

1. клиент должен существовать при создании заказа;
2. адреса обязательны и не могут совпадать;
3. вес и стоимость должны быть больше нуля;
4. переходы статусов выполняются только по разрешенной последовательности;
5. заказы в пути и доставленные заказы нельзя удалить;
6. доставленный заказ нельзя отменить или вернуть в предыдущий статус.

## Запуск PostgreSQL

### Вариант 1. Локальный PostgreSQL

Создай базу данных `delivery_db`, затем выполни скрипты из корня проекта:

```bash
psql -U postgres -c "CREATE DATABASE delivery_db;"
psql -U postgres -d delivery_db -v ON_ERROR_STOP=1 -f database/schema.sql
psql -U postgres -d delivery_db -v ON_ERROR_STOP=1 -f database/seed.sql
```

### Вариант 2. Docker (контейнер с базой КР №1)

Нужен установленный Docker Desktop. Из папки `database/`:

```bash
docker compose up -d --wait
```

- PostgreSQL поднимется на порту **55432** (внутри контейнера — стандартный 5432);
- `schema.sql` и `seed.sql` применяются автоматически при первом запуске пустого контейнера;
- данные сохраняются в volume `delivery-pgdata` — `docker compose down` их не удаляет;
- для остановки: `docker compose down`.

JDBC-строка для этого варианта: `jdbc:postgresql://localhost:55432/delivery_db`
(указывается в `javafx-client/src/main/resources/database.properties`).

Приложение автоматически читает `.env` из корня проекта. Переменные окружения Windows и Java-системные свойства имеют приоритет над `.env`.

## Сборка и запуск

```bash
mvn clean test
mvn exec:java
```

Или запусти класс `org.example.delivery.Main`.

## Начальные данные

`database/seed.sql` добавляет 5 клиентов и 10 заказов с 7 различными статусами.

---

# КР №3. Backend (Spring Boot REST API) и web-интерфейс

Каталог `backend/` — Spring Boot 3.5 + PostgreSQL 17 + Flyway + Spring Security/JWT + Swagger + Docker Compose.
Web-интерфейс (HTML/JS без сборки) отдаётся самим backend по адресу `http://localhost:8080/`.
REST API предназначен также для Android-приложения из КР №4 (`http://10.0.2.2:8080/api` из эмулятора).

## Перенос предметной области

| Student Hub (пример)   | Служба доставки                                    |
|------------------------|----------------------------------------------------|
| `Student`              | `DeliveryOrder` — заказ доставки (`/api/orders`)    |
| `StudyGroup`           | `Client` — клиент, справочник (`/api/clients`)      |
| `StudentStatus`        | `DeliveryStatus` — статусы из КР №1 с правилами переходов |
| `RiskLevel`            | `PriorityLevel` — вычисляемый приоритет заказа      |
| `StudentNote`          | `OrderNote` — комментарии к заказу                  |
| `student_tag`          | `order_tag` — теги заказа                           |
| роли VIEWER/TEACHER/ADMIN | `VIEWER` (просмотр), `DISPATCHER` (редактирование), `ADMIN` |

Поля заказа: UUID, номер `order_number` (внешний идентификатор), клиент, адреса, описание,
вес и стоимость (числовые показатели), `fragile` (логический признак), статус, теги,
приоритет (вычисляемый), `created_at`/`updated_at`, `version`, архив.

**Приоритет** (`PriorityCalculator`): хрупкий +2; цена ≥ 1500 → +2, ≥ 800 → +1; вес ≥ 5 кг → +1;
посылка у курьера (PICKED_UP / IN_TRANSIT) → +1. Итог: ≥4 URGENT, 3 HIGH, 1–2 NORMAL, 0 LOW;
для DELIVERED/CANCELLED — NOT_APPLICABLE.

## База данных и Flyway

- `V1__kr1_initial_schema.sql` — схема КР №1 без изменений;
- `V2__kr1_seed_data.sql` — данные КР №1;
- `V3__backend_schema.sql` — развитие: переход `clients`/`delivery_orders` с BIGSERIAL на UUID
  с сохранением данных, новые поля заказа, пользователи/роли, refresh-сессии, теги, комментарии, аудит, индексы.

Hibernate работает в режиме `ddl-auto: validate`.

## Запуск

Нужен Docker Desktop. Файл `.env` лежит в корне (шаблон — `.env.example`).

```powershell
powershell -ExecutionPolicy Bypass -File .\backend\start-compose.ps1
# или
docker compose --env-file .env -f backend/docker-compose.yml up -d --build --wait
```

| Назначение   | Адрес |
|--------------|-------|
| Web UI       | http://localhost:8080/ |
| Swagger UI   | http://localhost:8080/swagger-ui.html (только ADMIN; вход через web UI) |
| REST API     | http://localhost:8080/api |
| Health       | http://localhost:8080/actuator/health |
| PostgreSQL   | `jdbc:postgresql://localhost:55433/delivery_kr3` (порт 55433, чтобы не конфликтовать с БД КР №1/2 на 55432) |

Первый вход: `admin@example.org` и временный пароль `BOOTSTRAP_ADMIN_PASSWORD` из `.env`;
система сразу потребует сменить его на постоянный.

Остановка: `.\backend\stop-compose.ps1` (данные сохраняются). Полный сброс БД: добавить `-v` к `docker compose down`.

Тесты (H2, без Docker-БД): `mvn -f backend/pom.xml test` — smoke-тест контекста, приоритет,
переходы статусов, умный фильтр, политика паролей, безопасность API.

## Основные эндпоинты

| Назначение | Метод и адрес |
|---|---|
| Вход / refresh / выход / текущий пользователь | `POST /api/auth/login`, `POST /api/auth/refresh`, `POST /api/auth/logout`, `GET /api/auth/me` |
| Смена пароля / первичная смена | `POST /api/auth/change-password`, `POST /api/auth/change-initial-password` |
| Список / карточка / создание / изменение | `GET /api/orders`, `GET /api/orders/{id}`, `POST /api/orders`, `PUT /api/orders/{id}` |
| Архив / восстановление / удаление (ADMIN) | `DELETE /api/orders/{id}`, `POST /api/orders/{id}/restore`, `DELETE /api/orders/{id}/permanent` (+ заголовок `X-Confirm-Delete: PERMANENTLY_DELETE`) |
| Массовые операции | `POST /api/orders/bulk` (ARCHIVE, RESTORE, SET_STATUS, ADD_TAG, REMOVE_TAG) |
| CSV | `POST /api/orders/import/preview`, `POST /api/orders/import/apply`, `GET /api/orders/export?q=...` |
| Комментарии | `GET/POST /api/orders/{id}/notes`, `PUT/DELETE /api/orders/{id}/notes/{noteId}` |
| Клиенты | `GET /api/clients`, `POST /api/clients`, `PUT /api/clients/{id}`, `PATCH /api/clients/{id}/active` |
| Аналитика | `GET /api/analytics/summary` |
| Пользователи / аудит (ADMIN) | `GET/POST /api/admin/users`, `PUT /api/admin/users/{id}`, `POST /api/admin/users/{id}/reset-password`, `GET /api/admin/audit` |

Параметры списка заказов: `q` — умный фильтр, `page`, `size` (1–100), `sort` (`created`, `number`, `client`,
`status`, `price`, `weight`, `priority`, `updated` + `,asc|desc`).

Пример фильтра: `Книга клиент:Иванов статус:в_пути хрупкий:да цена>=500 вес<3 приоритет:высокий тег:срочно архив:все`.
Фильтр разбирается в типобезопасную модель и превращается в JPA Criteria с параметрами — без конкатенации SQL.
