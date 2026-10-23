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
