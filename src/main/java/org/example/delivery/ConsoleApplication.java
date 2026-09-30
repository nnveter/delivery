package org.example.delivery;

import org.example.delivery.exception.BusinessException;
import org.example.delivery.exception.DatabaseException;
import org.example.delivery.exception.EntityNotFoundException;
import org.example.delivery.model.Client;
import org.example.delivery.model.DeliveryOrder;
import org.example.delivery.model.DeliveryStatus;
import org.example.delivery.repository.ClientRepository;
import org.example.delivery.repository.DeliveryOrderRepository;
import org.example.delivery.service.ClientService;
import org.example.delivery.service.DeliveryOrderService;
import org.example.delivery.service.DeliveryStatistics;
import org.example.delivery.util.ExcelExporter;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.List;
import java.util.Scanner;

public final class ConsoleApplication {
    private final Scanner scanner = new Scanner(System.in);
    private final ClientService clientService;
    private final DeliveryOrderService orderService;
    private final ExcelExporter excelExporter = new ExcelExporter();

    public ConsoleApplication() {
        ClientRepository clientRepository = new ClientRepository();
        this.clientService = new ClientService(clientRepository);
        this.orderService = new DeliveryOrderService(new DeliveryOrderRepository(), clientRepository);
    }

    public void run() {
        printHeader();
        boolean running = true;
        while (running) {
            printMainMenu();
            String choice = scanner.nextLine().trim();
            try {
                running = switch (choice) {
                    case "1" -> {
                        clientMenu();
                        yield true;
                    }
                    case "2" -> {
                        orderMenu();
                        yield true;
                    }
                    case "3" -> {
                        searchMenu();
                        yield true;
                    }
                    case "4" -> {
                        filterAndSortMenu();
                        yield true;
                    }
                    case "5" -> {
                        showStatistics();
                        yield true;
                    }
                    case "6" -> {
                        exportData();
                        yield true;
                    }
                    case "7" -> {
                        showDatabaseTables();
                        yield true;
                    }
                    case "0" -> false;
                    default -> {
                        System.out.println("Неизвестный пункт меню.");
                        yield true;
                    }
                };
            } catch (DatabaseException | BusinessException | EntityNotFoundException | IllegalArgumentException exception) {
                System.out.println("Ошибка: " + exception.getMessage());
            } catch (RuntimeException exception) {
                System.out.println("Непредвиденная ошибка: " + exception.getMessage());
            }
        }
        System.out.println("Работа завершена.");
    }

    private void clientMenu() {
        boolean back = false;
        while (!back) {
            System.out.println("""

                    --- КЛИЕНТЫ ---
                    1. Создать клиента
                    2. Показать всех клиентов
                    3. Найти клиента по ID
                    4. Изменить клиента
                    5. Удалить клиента
                    6. Поиск клиентов
                    0. Назад
                    """);
            try {
                switch (scanner.nextLine().trim()) {
                    case "1" -> createClient();
                    case "2" -> printClients(clientService.findAll());
                    case "3" -> printClient(clientService.findById(readLong("ID: ")));
                    case "4" -> updateClient();
                    case "5" -> clientService.delete(readLong("ID: "));
                    case "6" -> printClients(clientService.search(readRequired("Поиск: ")));
                    case "0" -> back = true;
                    default -> System.out.println("Неизвестный пункт меню.");
                }
            } catch (RuntimeException exception) {
                System.out.println("Ошибка: " + exception.getMessage());
            }
        }
    }

    private void orderMenu() {
        boolean back = false;
        while (!back) {
            System.out.println("""

                    --- ЗАКАЗЫ ---
                    1. Создать заказ
                    2. Показать все заказы
                    3. Найти заказ по ID
                    4. Изменить заказ
                    5. Изменить статус
                    6. Удалить заказ
                    0. Назад
                    """);
            try {
                switch (scanner.nextLine().trim()) {
                    case "1" -> createOrder();
                    case "2" -> printOrders(orderService.findAll());
                    case "3" -> printOrder(orderService.findById(readLong("ID: ")));
                    case "4" -> updateOrder();
                    case "5" -> changeOrderStatus();
                    case "6" -> orderService.delete(readLong("ID: "));
                    case "0" -> back = true;
                    default -> System.out.println("Неизвестный пункт меню.");
                }
            } catch (RuntimeException exception) {
                System.out.println("Ошибка: " + exception.getMessage());
            }
        }
    }

    private void searchMenu() {
        String query = readRequired("Искомое значение (клиент, телефон или адрес): ");
        printOrders(orderService.search(query));
    }

    private void filterAndSortMenu() {
        System.out.println("""

                --- ФИЛЬТРАЦИЯ И СОРТИРОВКА ---
                1. Фильтр по статусу
                2. Фильтр по диапазону стоимости
                3. Фильтр по статусу и стоимости
                4. Сортировка по цене
                5. Сортировка по весу
                6. Сортировка по дате создания
                """);
        String choice = scanner.nextLine().trim();
        switch (choice) {
            case "1" -> printOrders(orderService.filter(readStatus(), null, null));
            case "2" -> printOrders(orderService.filter(null, readNullableDecimal("От: "), readNullableDecimal("До: ")));
            case "3" -> printOrders(orderService.filter(
                    readStatus(), readNullableDecimal("От: "), readNullableDecimal("До: ")));
            case "4" -> printOrders(orderService.sort("price", readDescending()));
            case "5" -> printOrders(orderService.sort("weight", readDescending()));
            case "6" -> printOrders(orderService.sort("created_at", readDescending()));
            default -> System.out.println("Неизвестный пункт меню.");
        }
    }

    private void createClient() {
        Client client = clientService.create(
                readRequired("ФИО: "),
                readRequired("Телефон: "),
                readOptional("Email: "),
                readRequired("Адрес: "));
        System.out.println("Создан клиент с ID " + client.getId());
    }

    private void updateClient() {
        long id = readLong("ID клиента: ");
        Client client = clientService.update(
                id,
                readRequired("ФИО: "),
                readRequired("Телефон: "),
                readOptional("Email: "),
                readRequired("Адрес: "));
        System.out.println("Клиент изменен: " + client);
    }

    private void createOrder() {
        DeliveryOrder order = orderService.create(
                readLong("ID клиента: "),
                readRequired("Адрес отправления: "),
                readRequired("Адрес доставки: "),
                readOptional("Описание: "),
                readDecimal("Вес, кг: "),
                readDecimal("Стоимость, руб.: "));
        System.out.println("Создан заказ с ID " + order.getId());
    }

    private void updateOrder() {
        long id = readLong("ID заказа: ");
        DeliveryOrder current = orderService.findById(id);
        DeliveryOrder order = orderService.update(
                id,
                readLong("ID клиента: "),
                readRequired("Адрес отправления: "),
                readRequired("Адрес доставки: "),
                readOptional("Описание: "),
                readDecimal("Вес, кг: "),
                readDecimal("Стоимость, руб.: "));
        System.out.println("Заказ изменен. Текущий статус: " + current.getStatus());
        printOrder(order);
    }

    private void changeOrderStatus() {
        long id = readLong("ID заказа: ");
        DeliveryStatus status = readStatus();
        printOrder(orderService.changeStatus(id, status));
    }

    private void showStatistics() {
        DeliveryStatistics statistics = orderService.statistics();
        System.out.println("""

                --- СТАТИСТИКА ---
                Клиентов: %d
                Всего заказов: %d
                Активных заказов: %d
                Доставлено: %d
                Отменено: %d
                Выручка по доставленным заказам: %s руб.
                """.formatted(
                statistics.totalClients(),
                statistics.totalOrders(),
                statistics.activeOrders(),
                statistics.deliveredOrders(),
                statistics.cancelledOrders(),
                statistics.deliveredRevenue()));
    }

    private void exportData() {
        String fileName = readOptional("Имя файла [delivery-export.xlsx]: ");
        if (fileName.isBlank()) {
            fileName = "delivery-export.xlsx";
        }
        Path path = excelExporter.export(
                Path.of(fileName), clientService.findAll(), orderService.findAll());
        System.out.println("Экспорт завершен: " + path.toAbsolutePath());
    }

    private void showDatabaseTables() {
        System.out.println("\nТаблица clients:");
        printClients(clientService.findAll());
        System.out.println("\nТаблица delivery_orders:");
        printOrders(orderService.findAll());
    }

    private void printHeader() {
        System.out.println("""
                ========================================
                       СИСТЕМА СЛУЖБЫ ДОСТАВКИ
                ========================================
                """);
    }

    private void printMainMenu() {
        System.out.println("""
                1. Управление клиентами
                2. Управление заказами
                3. Поиск заказов
                4. Фильтрация и сортировка
                5. Статистика
                6. Экспорт в Excel
                7. Вывести таблицы базы данных
                0. Выход
                Выберите действие:
                """);
    }

    private void printClients(List<Client> clients) {
        if (clients.isEmpty()) {
            System.out.println("Клиенты не найдены.");
            return;
        }
        clients.forEach(System.out::println);
    }

    private void printOrders(List<DeliveryOrder> orders) {
        if (orders.isEmpty()) {
            System.out.println("Заказы не найдены.");
            return;
        }
        orders.forEach(System.out::println);
    }

    private void printClient(Client client) {
        System.out.println(client);
    }

    private void printOrder(DeliveryOrder order) {
        System.out.println(order);
    }

    private String readRequired(String prompt) {
        while (true) {
            System.out.print(prompt);
            String value = scanner.nextLine().trim();
            if (!value.isBlank()) {
                return value;
            }
            System.out.println("Значение не может быть пустым.");
        }
    }

    private String readOptional(String prompt) {
        System.out.print(prompt);
        String value = scanner.nextLine().trim();
        return value.isBlank() ? null : value;
    }

    private long readLong(String prompt) {
        while (true) {
            try {
                System.out.print(prompt);
                return Long.parseLong(scanner.nextLine().trim());
            } catch (NumberFormatException exception) {
                System.out.println("Ошибка: ID должен быть целым числом.");
            }
        }
    }

    private BigDecimal readDecimal(String prompt) {
        while (true) {
            try {
                System.out.print(prompt);
                return new BigDecimal(scanner.nextLine().trim().replace(',', '.'));
            } catch (NumberFormatException exception) {
                System.out.println("Ошибка: введите число.");
            }
        }
    }

    private BigDecimal readNullableDecimal(String prompt) {
        System.out.print(prompt);
        String value = scanner.nextLine().trim();
        if (value.isBlank()) {
            return null;
        }
        try {
            return new BigDecimal(value.replace(',', '.'));
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Введите корректное число или оставьте поле пустым.");
        }
    }

    private DeliveryStatus readStatus() {
        System.out.println("Доступные статусы: " + String.join(", ",
                java.util.Arrays.stream(DeliveryStatus.values()).map(Enum::name).toList()));
        while (true) {
            String value = readRequired("Статус: ").toUpperCase();
            try {
                return DeliveryStatus.valueOf(value);
            } catch (IllegalArgumentException exception) {
                System.out.println("Неизвестный статус.");
            }
        }
    }

    private boolean readDescending() {
        System.out.print("По убыванию? (да/нет): ");
        return scanner.nextLine().trim().equalsIgnoreCase("да");
    }
}
