package org.example.delivery.fx.controller;

import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import org.example.delivery.exception.BusinessException;
import org.example.delivery.fx.util.UiText;
import org.example.delivery.model.Client;
import org.example.delivery.model.DeliveryOrder;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Контроллер диалогового окна добавления/редактирования заказа.
 * Только собирает и проверяет ввод: сохранение выполняет MainController через Service.
 */
public class OrderDialogController {
    @FXML private Label formTitle;
    @FXML private ComboBox<Client> clientComboBox;
    @FXML private TextField pickupField;
    @FXML private TextField deliveryField;
    @FXML private TextArea descriptionField;
    @FXML private TextField weightField;
    @FXML private TextField priceField;

    private boolean saved;

    public void init(List<Client> clients, DeliveryOrder existing) {
        clientComboBox.setItems(FXCollections.observableArrayList(clients));
        clientComboBox.setConverter(new javafx.util.StringConverter<>() {
            @Override
            public String toString(Client client) {
                if (client == null) {
                    return "";
                }
                return client.getFullName() + " (" + client.getPhone() + ")";
            }

            @Override
            public Client fromString(String string) {
                return null;
            }
        });

        if (existing == null) {
            formTitle.setText("Новый заказ");
        } else {
            formTitle.setText("Редактирование заказа №" + existing.getId());
            Client selected = clients.stream()
                    .filter(client -> client.getId().equals(existing.getClientId()))
                    .findFirst()
                    .orElse(null);
            clientComboBox.getSelectionModel().select(selected);
            pickupField.setText(existing.getPickupAddress());
            deliveryField.setText(existing.getDeliveryAddress());
            descriptionField.setText(existing.getDescription());
            weightField.setText(existing.getWeight().toPlainString());
            priceField.setText(existing.getPrice().toPlainString());
        }
    }

    public boolean isSaved() {
        return saved;
    }

    public Long getClientId() {
        Client client = clientComboBox.getValue();
        return client == null ? null : client.getId();
    }

    public String getPickupAddress() {
        return pickupField.getText();
    }

    public String getDeliveryAddress() {
        return deliveryField.getText();
    }

    public String getDescription() {
        String text = descriptionField.getText();
        return text == null ? "" : text.trim();
    }

    public BigDecimal getWeight() {
        return new BigDecimal(weightField.getText().trim().replace(',', '.'));
    }

    public BigDecimal getPrice() {
        return new BigDecimal(priceField.getText().trim().replace(',', '.'));
    }

    @FXML
    private void onSave() {
        List<String> errors = new ArrayList<>();

        if (clientComboBox.getValue() == null) {
            errors.add("— выберите клиента из списка;");
        }
        requireText(pickupField.getText(), "Адрес отправления", errors);
        requireText(deliveryField.getText(), "Адрес доставки", errors);
        if (pickupField.getText() != null && deliveryField.getText() != null
                && !pickupField.getText().isBlank() && !deliveryField.getText().isBlank()
                && pickupField.getText().trim().equalsIgnoreCase(deliveryField.getText().trim())) {
            errors.add("— адреса отправления и доставки должны различаться;");
        }
        BigDecimal weight = requirePositiveNumber(weightField.getText(), "Вес", errors);
        BigDecimal price = requirePositiveNumber(priceField.getText(), "Стоимость", errors);
        if (weight != null && weight.signum() <= 0) {
            errors.add("— вес должен быть больше нуля;");
        }
        if (price != null && price.signum() <= 0) {
            errors.add("— стоимость должна быть больше нуля;");
        }

        if (!errors.isEmpty()) {
            showError("Исправьте ошибки в форме:\n" + String.join("\n", errors));
            return;
        }
        saved = true;
        close();
    }

    @FXML
    private void onCancel() {
        saved = false;
        close();
    }

    private void requireText(String value, String fieldName, List<String> errors) {
        if (value == null || value.isBlank()) {
            errors.add("— заполните поле «" + fieldName + "»;");
        }
    }

    private BigDecimal requirePositiveNumber(String text, String fieldName, List<String> errors) {
        if (text == null || text.isBlank()) {
            errors.add("— заполните поле «" + fieldName + "»;");
            return null;
        }
        try {
            return new BigDecimal(text.trim().replace(',', '.'));
        } catch (NumberFormatException exception) {
            errors.add("— «" + fieldName + "» должно быть числом (например, 2.5);");
            return null;
        }
    }

    private void showError(String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR, message, ButtonType.OK);
        alert.setTitle("Проверка данных");
        alert.setHeaderText("Форма заполнена неверно");
        alert.showAndWait();
    }

    private void close() {
        formTitle.getScene().getWindow().hide();
    }
}
