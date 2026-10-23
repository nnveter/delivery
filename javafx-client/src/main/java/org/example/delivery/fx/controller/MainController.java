package org.example.delivery.fx.controller;

import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ChoiceDialog;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TabPane;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;
import org.example.delivery.exception.BusinessException;
import org.example.delivery.fx.util.CsvExporter;
import org.example.delivery.fx.util.UiText;
import org.example.delivery.model.Client;
import org.example.delivery.model.DeliveryOrder;
import org.example.delivery.model.DeliveryStatus;
import org.example.delivery.repository.ClientRepository;
import org.example.delivery.repository.DeliveryOrderRepository;
import org.example.delivery.service.ClientService;
import org.example.delivery.service.DeliveryOrderService;

import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;

/**
 * Главный контроллер: таблицы заказов и клиентов, поиск, фильтрация,
 * сортировка, CRUD, статистика и экспорт.
 * SQL и бизнес-правила находятся в слое Service/Repository — здесь только координация UI.
 */
public class MainController {
    private static final String ALL_STATUSES = "Все статусы";

    private final ClientService clientService = new ClientService(new ClientRepository());
    private final DeliveryOrderService orderService = new DeliveryOrderService(
            new DeliveryOrderRepository(), new ClientRepository());

    @FXML private TabPane tabPane;

    @FXML private TableView<DeliveryOrder> ordersTable;
    @FXML private TableColumn<DeliveryOrder, Number> orderIdColumn;
    @FXML private TableColumn<DeliveryOrder, String> orderClientColumn;
    @FXML private TableColumn<DeliveryOrder, String> orderPickupColumn;
    @FXML private TableColumn<DeliveryOrder, String> orderDeliveryColumn;
    @FXML private TableColumn<DeliveryOrder, String> orderWeightColumn;
    @FXML private TableColumn<DeliveryOrder, String> orderPriceColumn;
    @FXML private TableColumn<DeliveryOrder, String> orderCreatedColumn;
    @FXML private TableColumn<DeliveryOrder, String> orderStatusColumn;
    @FXML private TextField orderSearchField;
    @FXML private ComboBox<String> orderStatusFilter;
    @FXML private TextField minPriceField;
    @FXML private TextField maxPriceField;
    @FXML private ComboBox<String> orderSortField;
    @FXML private ComboBox<String> orderSortDirection;

    @FXML private TableView<Client> clientsTable;
    @FXML private TableColumn<Client, Number> clientIdColumn;
    @FXML private TableColumn<Client, String> clientNameColumn;
    @FXML private TableColumn<Client, String> clientPhoneColumn;
    @FXML private TableColumn<Client, String> clientEmailColumn;
    @FXML private TableColumn<Client, String> clientAddressColumn;
    @FXML private TextField clientSearchField;

    @FXML private Label statusBar;

    @FXML
    private void initialize() {
        setupOrderColumns();
        setupClientColumns();

        orderStatusFilter.getItems().add(ALL_STATUSES);
        for (DeliveryStatus statusValue : DeliveryStatus.values()) {
            orderStatusFilter.getItems().add(UiText.statusName(statusValue));
        }
        orderStatusFilter.getSelectionModel().selectFirst();

        orderSortField.getItems().addAll(
                "По дате создания", "По стоимости", "По весу", "По ID");
        orderSortField.getSelectionModel().selectFirst();
        orderSortDirection.getItems().addAll("По возрастанию", "По убыванию");
        orderSortDirection.getSelectionModel().selectFirst();

        reloadOrders();
        reloadClients();
        status("Приложение готово к работе. Данные загружены из PostgreSQL.");
    }

    // ---------- Настройка таблиц ----------

    private void setupOrderColumns() {
        orderIdColumn.setCellValueFactory(c ->
                new ReadOnlyObjectWrapper<>(c.getValue().getId()));
        orderClientColumn.setCellValueFactory(c ->
                new ReadOnlyStringWrapper(c.getValue().getClientName()));
        orderPickupColumn.setCellValueFactory(c ->
                new ReadOnlyStringWrapper(c.getValue().getPickupAddress()));
        orderDeliveryColumn.setCellValueFactory(c ->
                new ReadOnlyStringWrapper(c.getValue().getDeliveryAddress()));
        orderWeightColumn.setCellValueFactory(c ->
                new ReadOnlyStringWrapper(UiText.weight(c.getValue().getWeight())));
        orderPriceColumn.setCellValueFactory(c ->
                new ReadOnlyStringWrapper(UiText.money(c.getValue().getPrice())));
        orderCreatedColumn.setCellValueFactory(c ->
                new ReadOnlyStringWrapper(UiText.dateTime(c.getValue().getCreatedAt())));
        orderStatusColumn.setCellValueFactory(c ->
                new ReadOnlyStringWrapper(UiText.statusName(c.getValue().getStatus())));
    }

    private void setupClientColumns() {
        clientIdColumn.setCellValueFactory(c ->
                new ReadOnlyObjectWrapper<>(c.getValue().getId()));
        clientNameColumn.setCellValueFactory(c ->
                new ReadOnlyStringWrapper(c.getValue().getFullName()));
        clientPhoneColumn.setCellValueFactory(c ->
                new ReadOnlyStringWrapper(c.getValue().getPhone()));
        clientEmailColumn.setCellValueFactory(c ->
                new ReadOnlyStringWrapper(c.getValue().getEmail() == null ? "—" : c.getValue().getEmail()));
        clientAddressColumn.setCellValueFactory(c ->
                new ReadOnlyStringWrapper(c.getValue().getAddress()));
    }

    // ---------- Заказы: загрузка, поиск, фильтрация, сортировка ----------

    @FXML
    private void onRefreshOrders() {
        orderSearchField.clear();
        orderStatusFilter.getSelectionModel().selectFirst();
        minPriceField.clear();
        maxPriceField.clear();
        reloadOrders();
        status("Список заказов обновлён.");
    }

    private void reloadOrders() {
        ordersTable.setItems(FXCollections.observableArrayList(orderService.findAll()));
    }

    @FXML
    private void onSearchOrders() {
        String query = orderSearchField.getText();
        try {
            List<DeliveryOrder> result = query == null || query.isBlank()
                    ? orderService.findAll()
                    : orderService.search(query.trim());
            ordersTable.setItems(FXCollections.observableArrayList(result));
            status("Поиск заказов: найдено " + result.size() + ".");
        } catch (RuntimeException exception) {
            showError(exception.getMessage());
        }
    }

    @FXML
    private void onFilterOrders() {
        try {
            DeliveryStatus selectedStatus = selectedStatusFilter();
            BigDecimal minPrice = parsePrice(minPriceField, "Минимальная цена");
            BigDecimal maxPrice = parsePrice(maxPriceField, "Максимальная цена");
            List<DeliveryOrder> result = orderService.filter(selectedStatus, minPrice, maxPrice);
            ordersTable.setItems(FXCollections.observableArrayList(result));
            status("Фильтрация: показано " + result.size() + " заказов.");
        } catch (RuntimeException exception) {
            showError(exception.getMessage());
        }
    }

    @FXML
    private void onSortOrders() {
        try {
            String key = switch (orderSortField.getValue()) {
                case "По стоимости" -> "price";
                case "По весу" -> "weight";
                case "По ID" -> "id";
                default -> "created_at";
            };
            boolean descending = "По убыванию".equals(orderSortDirection.getValue());
            List<DeliveryOrder> result = orderService.sort(key, descending);
            ordersTable.setItems(FXCollections.observableArrayList(result));
            status("Сортировка: " + orderSortField.getValue().toLowerCase()
                    + ", " + orderSortDirection.getValue().toLowerCase() + ".");
        } catch (RuntimeException exception) {
            showError(exception.getMessage());
        }
    }

    private DeliveryStatus selectedStatusFilter() {
        String selected = orderStatusFilter.getValue();
        if (selected == null || ALL_STATUSES.equals(selected)) {
            return null;
        }
        for (DeliveryStatus statusValue : DeliveryStatus.values()) {
            if (UiText.statusName(statusValue).equals(selected)) {
                return statusValue;
            }
        }
        return null;
    }

    private BigDecimal parsePrice(TextField field, String fieldName) {
        String text = field.getText();
        if (text == null || text.isBlank()) {
            return null;
        }
        try {
            return new BigDecimal(text.trim().replace(',', '.'));
        } catch (NumberFormatException exception) {
            throw new BusinessException(fieldName + " должна быть числом (например, 150 или 150.50).");
        }
    }

    // ---------- Заказы: CRUD ----------

    @FXML
    private void onAddOrder() {
        openOrderDialog(null);
    }

    @FXML
    private void onEditOrder() {
        DeliveryOrder selected = ordersTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            showError("Сначала выберите заказ в таблице.");
            return;
        }
        openOrderDialog(selected);
    }

    private void openOrderDialog(DeliveryOrder existing) {
        try {
            FXMLLoader loader = new FXMLLoader(
                    getClass().getResource("/views/order-dialog.fxml"));
            Parent root = loader.load();
            OrderDialogController controller = loader.getController();
            controller.init(clientService.findAll(), existing);

            Stage dialog = new Stage();
            dialog.initOwner(ordersTable.getScene().getWindow());
            dialog.initModality(Modality.WINDOW_MODAL);
            dialog.setTitle(existing == null
                    ? "Новый заказ"
                    : "Заказ №" + existing.getId() + " — редактирование");
            dialog.setScene(new Scene(root));
            dialog.showAndWait();

            if (!controller.isSaved()) {
                return;
            }
            if (existing == null) {
                orderService.create(controller.getClientId(), controller.getPickupAddress(),
                        controller.getDeliveryAddress(), controller.getDescription(),
                        controller.getWeight(), controller.getPrice());
                status("Новый заказ сохранён.");
            } else {
                orderService.update(existing.getId(), controller.getClientId(),
                        controller.getPickupAddress(), controller.getDeliveryAddress(),
                        controller.getDescription(), controller.getWeight(), controller.getPrice());
                status("Заказ №" + existing.getId() + " обновлён.");
            }
            reloadOrders();
        } catch (IOException exception) {
            showError("Не удалось открыть окно заказа: " + exception.getMessage());
        } catch (RuntimeException exception) {
            showError(exception.getMessage());
            reloadOrders();
        }
    }

    @FXML
    private void onDeleteOrder() {
        DeliveryOrder selected = ordersTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            showError("Сначала выберите заказ в таблице.");
            return;
        }
        Alert confirmation = new Alert(Alert.AlertType.CONFIRMATION,
                "Удалить заказ №" + selected.getId() + " («" + selected.getPickupAddress()
                        + " → " + selected.getDeliveryAddress() + "»)?",
                ButtonType.OK, ButtonType.CANCEL);
        confirmation.setTitle("Подтверждение удаления");
        confirmation.setHeaderText("Удаление заказа");
        confirmation.showAndWait().ifPresent(buttonType -> {
            if (buttonType == ButtonType.OK) {
                try {
                    orderService.delete(selected.getId());
                    status("Заказ №" + selected.getId() + " удалён.");
                    reloadOrders();
                } catch (RuntimeException exception) {
                    showError(exception.getMessage());
                }
            }
        });
    }

    @FXML
    private void onChangeOrderStatus() {
        DeliveryOrder selected = ordersTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            showError("Сначала выберите заказ в таблице.");
            return;
        }
        List<DeliveryStatus> allowed = Arrays.stream(DeliveryStatus.values())
                .filter(target -> selected.getStatus().canTransitionTo(target))
                .toList();
        if (allowed.isEmpty()) {
            showInfo("Заказ №" + selected.getId() + " находится в конечном статусе «"
                    + UiText.statusName(selected.getStatus())
                    + "» — сменить статус нельзя.");
            return;
        }
        ChoiceDialog<DeliveryStatus> dialog = new ChoiceDialog<>(allowed.get(0), allowed);
        dialog.setTitle("Смена статуса");
        dialog.setHeaderText("Смена статуса заказа №" + selected.getId());
        dialog.setContentText("Текущий статус: " + UiText.statusName(selected.getStatus())
                + ". Выберите новый:");
        dialog.showAndWait().ifPresent(target -> {
            try {
                orderService.changeStatus(selected.getId(), target);
                status("Статус заказа №" + selected.getId() + " изменён на «"
                        + UiText.statusName(target) + "».");
                reloadOrders();
            } catch (RuntimeException exception) {
                showError(exception.getMessage());
            }
        });
    }

    @FXML
    private void onShowOrderDetails() {
        DeliveryOrder selected = ordersTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            showError("Сначала выберите заказ в таблице.");
            return;
        }
        String details = """
                Заказ №%d
                Клиент: %s
                Адрес отправления: %s
                Адрес доставки: %s
                Описание: %s
                Вес: %s
                Стоимость: %s
                Создан: %s
                Статус: %s
                """.formatted(
                selected.getId(),
                selected.getClientName(),
                selected.getPickupAddress(),
                selected.getDeliveryAddress(),
                selected.getDescription() == null || selected.getDescription().isBlank()
                        ? "—" : selected.getDescription(),
                UiText.weight(selected.getWeight()),
                UiText.money(selected.getPrice()),
                UiText.dateTime(selected.getCreatedAt()),
                UiText.statusName(selected.getStatus()));
        showInfo(details);
    }

    // ---------- Клиенты: поиск и CRUD ----------

    @FXML
    private void onRefreshClients() {
        clientSearchField.clear();
        reloadClients();
        status("Список клиентов обновлён.");
    }

    private void reloadClients() {
        clientsTable.setItems(FXCollections.observableArrayList(clientService.findAll()));
    }

    @FXML
    private void onSearchClients() {
        String query = clientSearchField.getText();
        try {
            List<Client> result = query == null || query.isBlank()
                    ? clientService.findAll()
                    : clientService.search(query.trim());
            clientsTable.setItems(FXCollections.observableArrayList(result));
            status("Поиск клиентов: найдено " + result.size() + ".");
        } catch (RuntimeException exception) {
            showError(exception.getMessage());
        }
    }

    @FXML
    private void onAddClient() {
        openClientDialog(null);
    }

    @FXML
    private void onEditClient() {
        Client selected = clientsTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            showError("Сначала выберите клиента в таблице.");
            return;
        }
        openClientDialog(selected);
    }

    private void openClientDialog(Client existing) {
        try {
            FXMLLoader loader = new FXMLLoader(
                    getClass().getResource("/views/client-dialog.fxml"));
            Parent root = loader.load();
            ClientDialogController controller = loader.getController();
            controller.init(existing);

            Stage dialog = new Stage();
            dialog.initOwner(clientsTable.getScene().getWindow());
            dialog.initModality(Modality.WINDOW_MODAL);
            dialog.setTitle(existing == null
                    ? "Новый клиент"
                    : "Клиент: " + existing.getFullName() + " — редактирование");
            dialog.setScene(new Scene(root));
            dialog.showAndWait();

            if (!controller.isSaved()) {
                return;
            }
            if (existing == null) {
                clientService.create(controller.getFullName(), controller.getPhone(),
                        controller.getEmail(), controller.getAddress());
                status("Новый клиент сохранён.");
            } else {
                clientService.update(existing.getId(), controller.getFullName(),
                        controller.getPhone(), controller.getEmail(), controller.getAddress());
                status("Клиент «" + controller.getFullName() + "» обновлён.");
            }
            reloadClients();
        } catch (IOException exception) {
            showError("Не удалось открыть окно клиента: " + exception.getMessage());
        } catch (RuntimeException exception) {
            showError(exception.getMessage());
            reloadClients();
        }
    }

    @FXML
    private void onDeleteClient() {
        Client selected = clientsTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            showError("Сначала выберите клиента в таблице.");
            return;
        }
        Alert confirmation = new Alert(Alert.AlertType.CONFIRMATION,
                "Удалить клиента «" + selected.getFullName() + "»?",
                ButtonType.OK, ButtonType.CANCEL);
        confirmation.setTitle("Подтверждение удаления");
        confirmation.setHeaderText("Удаление клиента");
        confirmation.showAndWait().ifPresent(buttonType -> {
            if (buttonType == ButtonType.OK) {
                try {
                    clientService.delete(selected.getId());
                    status("Клиент «" + selected.getFullName() + "» удалён.");
                    reloadClients();
                } catch (RuntimeException exception) {
                    showError(exception.getMessage());
                }
            }
        });
    }

    // ---------- Статистика и экспорт ----------

    @FXML
    private void onShowStatistics() {
        try {
            FXMLLoader loader = new FXMLLoader(
                    getClass().getResource("/views/statistics-view.fxml"));
            Parent root = loader.load();
            StatisticsController controller = loader.getController();
            controller.show(orderService.statistics());

            Stage dialog = new Stage();
            dialog.initOwner(tabPane.getScene().getWindow());
            dialog.initModality(Modality.WINDOW_MODAL);
            dialog.setTitle("Статистика системы доставки");
            dialog.setScene(new Scene(root));
            dialog.showAndWait();
        } catch (IOException exception) {
            showError("Не удалось открыть окно статистики: " + exception.getMessage());
        } catch (RuntimeException exception) {
            showError(exception.getMessage());
        }
    }

    @FXML
    private void onExportOrders() {
        List<DeliveryOrder> rows = ordersTable.getItems();
        if (rows.isEmpty()) {
            showInfo("Нет данных для экспорта: таблица заказов пуста.");
            return;
        }
        File file = chooseSaveFile("orders.csv");
        if (file == null) {
            return;
        }
        try {
            CsvExporter.exportOrders(file.toPath(), rows);
            status("Экспорт выполнен: " + rows.size() + " заказов → " + file.getName());
            showInfo("Данные сохранены в файл:\n" + file.getAbsolutePath());
        } catch (RuntimeException exception) {
            showError(exception.getMessage());
        }
    }

    @FXML
    private void onExportClients() {
        List<Client> rows = clientsTable.getItems();
        if (rows.isEmpty()) {
            showInfo("Нет данных для экспорта: таблица клиентов пуста.");
            return;
        }
        File file = chooseSaveFile("clients.csv");
        if (file == null) {
            return;
        }
        try {
            CsvExporter.exportClients(file.toPath(), rows);
            status("Экспорт выполнен: " + rows.size() + " клиентов → " + file.getName());
            showInfo("Данные сохранены в файл:\n" + file.getAbsolutePath());
        } catch (RuntimeException exception) {
            showError(exception.getMessage());
        }
    }

    private File chooseSaveFile(String defaultFileName) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Сохранить данные");
        chooser.setInitialFileName(defaultFileName);
        chooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("CSV файлы (*.csv)", "*.csv"));
        return chooser.showSaveDialog(statusBar.getScene().getWindow());
    }

    // ---------- Сообщения пользователю ----------

    private void status(String message) {
        statusBar.setText(message);
    }

    private void showError(String message) {
        statusBar.setText("Ошибка: " + message);
        Alert alert = new Alert(Alert.AlertType.ERROR, message, ButtonType.OK);
        alert.setTitle("Ошибка");
        alert.setHeaderText("Операция не выполнена");
        alert.showAndWait();
    }

    private void showInfo(String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION, message, ButtonType.OK);
        alert.setTitle("Информация");
        alert.setHeaderText(null);
        alert.showAndWait();
    }
}
