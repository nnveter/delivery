# Delivery JavaFX Client — КР №2

Настольное JavaFX-приложение службы доставки, подключённое **напрямую через JDBC** к базе
PostgreSQL `delivery_db`, созданной в КР №1. Gотовый REST API из архива примера не используется.

## Структура проекта

```
javafx-client/
├── pom.xml                                  Maven-конфигурация (Java 21, JavaFX 21, PostgreSQL JDBC)
├── run.ps1                                  скрипт запуска (mvn javafx:run)
└── src/main/
    ├── java/org/example/delivery/
    │   ├── fx/
    │   │   ├── DeliveryFxApplication.java   точка входа (Application, Stage/Scene)
    │   │   ├── controller/
    │   │   │   ├── MainController.java      главный контроллер: таблицы, поиск, фильтры, CRUD
    │   │   │   ├── OrderDialogController.java   форма заказа (валидация ввода)
    │   │   │   ├── ClientDialogController.java  форма клиента (валидация ввода)
    │   │   │   └── StatisticsController.java    окно статистики (11 показателей)
    │   │   └── util/
    │   │       ├── UiText.java              форматирование данных для интерфейса
    │   │       └── CsvExporter.java         экспорт в CSV (UTF-8 + BOM)
    │   ├── model/                           Client, DeliveryOrder, DeliveryStatus (из КР №1)
    │   ├── service/                         бизнес-правила (ClientService, DeliveryOrderService)
    │   ├── repository/                      DAO: JDBC + PreparedStatement
    │   ├── exception/                       BusinessException, DatabaseException, EntityNotFoundException
    │   └── util/DatabaseManager.java        JDBC-подключение из database.properties
    └── resources/
        ├── database.properties              параметры подключения к БД КР №1
        ├── views/                           FXML: main-view, order-dialog, client-dialog, statistics-view
        └── css/app.css                      оформление интерфейса
```

## Настройка подключения

Отредактируйте `src/main/resources/database.properties` под настройки своей базы из КР №1:

```
db.url=jdbc:postgresql://localhost:5432/delivery_db
db.user=postgres
db.password=пароль
```

Значения можно переопределить переменными окружения `DB_URL`, `DB_USER`, `DB_PASSWORD`
(пароль БД не хранится в исходном коде Java-классов).

## Запуск

```bash
mvn clean javafx:run
```

или `.\run.ps1` из каталога `javafx-client`, либо запуск `DeliveryFxApplication` из IntelliJ IDEA.

## Возможности

- две вкладки: заказы и клиенты (TableView + ObservableList);
- CRUD с диалоговыми окнами и подтверждением удаления;
- выбор клиента при создании заказа через ComboBox (без ввода ID);
- смена статуса заказа по разрешённым переходам;
- поиск: заказы (клиент, телефон, адреса), клиенты (ФИО, телефон, email);
- фильтры: статус + диапазон стоимости;
- сортировка: дата, стоимость, вес, ID (по возрастанию/убыванию);
- валидация ввода с понятными сообщениями об ошибках;
- статистика: 11 показателей (клиенты, заказы, статусы, выручка, средние значения, доля доставленных);
- экспорт текущей таблицы в CSV через FileChooser;
- слои: View (FXML) → Controller → Service → Repository (DAO/JDBC) → PostgreSQL;
- оформление вынесено в CSS.
