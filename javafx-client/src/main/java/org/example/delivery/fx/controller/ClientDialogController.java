package org.example.delivery.fx.controller;

import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;

import java.util.ArrayList;
import java.util.List;

/**
 * Контроллер диалогового окна добавления/редактирования клиента.
 * Выполняет локальную проверку формы; сохранение делает MainController через Service.
 */
public class ClientDialogController {
    @FXML private Label formTitle;
    @FXML private TextField fullNameField;
    @FXML private TextField phoneField;
    @FXML private TextField emailField;
    @FXML private TextArea addressField;

    private boolean saved;

    public void init(org.example.delivery.model.Client existing) {
        if (existing == null) {
            formTitle.setText("Новый клиент");
        } else {
            formTitle.setText("Редактирование клиента №" + existing.getId());
            fullNameField.setText(existing.getFullName());
            phoneField.setText(existing.getPhone());
            emailField.setText(existing.getEmail() == null ? "" : existing.getEmail());
            addressField.setText(existing.getAddress());
        }
    }

    public boolean isSaved() {
        return saved;
    }

    public String getFullName() {
        return fullNameField.getText();
    }

    public String getPhone() {
        return phoneField.getText();
    }

    public String getEmail() {
        String text = emailField.getText();
        return text == null || text.isBlank() ? null : text.trim();
    }

    public String getAddress() {
        return addressField.getText();
    }

    @FXML
    private void onSave() {
        List<String> errors = new ArrayList<>();
        if (fullNameField.getText() == null || fullNameField.getText().isBlank()) {
            errors.add("— заполните поле «ФИО»;");
        } else if (fullNameField.getText().trim().length() > 150) {
            errors.add("— ФИО не может быть длиннее 150 символов;");
        }
        if (phoneField.getText() == null || phoneField.getText().isBlank()) {
            errors.add("— заполните поле «Телефон»;");
        } else if (phoneField.getText().trim().length() > 30) {
            errors.add("— телефон не может быть длиннее 30 символов;");
        }
        if (addressField.getText() == null || addressField.getText().isBlank()) {
            errors.add("— заполните поле «Адрес»;");
        }
        String email = emailField.getText();
        if (email != null && !email.isBlank() && !email.trim().matches(".+@.+\\..+")) {
            errors.add("— email должен иметь вид «имя@домен.зона» (например, ivan@mail.ru);");
        }

        if (!errors.isEmpty()) {
            Alert alert = new Alert(Alert.AlertType.ERROR,
                    "Исправьте ошибки в форме:\n" + String.join("\n", errors), ButtonType.OK);
            alert.setTitle("Проверка данных");
            alert.setHeaderText("Форма заполнена неверно");
            alert.showAndWait();
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

    private void close() {
        formTitle.getScene().getWindow().hide();
    }
}
