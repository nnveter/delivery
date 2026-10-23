package org.example.delivery.fx;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.stage.Stage;
import org.example.delivery.exception.DatabaseException;

import java.io.IOException;

/**
 * Точка входа JavaFX-приложения (КР №2).
 * Приложение подключается напрямую к PostgreSQL из КР №1 через JDBC.
 */
public class DeliveryFxApplication extends Application {

    @Override
    public void start(Stage stage) {
        try {
            FXMLLoader loader = new FXMLLoader(
                    getClass().getResource("/views/main-view.fxml"));
            Parent root = loader.load();
            Scene scene = new Scene(root);
            scene.getStylesheets().add(
                    getClass().getResource("/css/app.css").toExternalForm());
            stage.setTitle("Служба доставки — JavaFX (КР №2)");
            stage.setMinWidth(1024);
            stage.setMinHeight(680);
            stage.setScene(scene);
            stage.show();
        } catch (IOException exception) {
            new Alert(Alert.AlertType.ERROR,
                    "Не удалось загрузить интерфейс: " + exception.getMessage()).showAndWait();
        } catch (DatabaseException exception) {
            new Alert(Alert.AlertType.ERROR, exception.getMessage()).showAndWait();
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}
