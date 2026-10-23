package org.example.delivery.util;

import org.example.delivery.exception.DatabaseException;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Properties;

/**
 * Подключение JavaFX-приложения к PostgreSQL из КР №1.
 *
 * Параметры читаются из файла database.properties в classpath
 * (src/main/resources/database.properties). Переменные окружения
 * DB_URL, DB_USER, DB_PASSWORD имеют приоритет над значениями из файла.
 */
public final class DatabaseManager {
    private static final Properties PROPERTIES = loadProperties();

    private DatabaseManager() {
    }

    public static Connection getConnection() {
        String url = value("DB_URL", "db.url", "jdbc:postgresql://localhost:5432/delivery_db");
        String user = value("DB_USER", "db.user", "postgres");
        String password = value("DB_PASSWORD", "db.password", "");
        try {
            return DriverManager.getConnection(url, user, password);
        } catch (SQLException exception) {
            throw new DatabaseException(
                    "Не удалось подключиться к базе данных. Проверьте настройки в database.properties.",
                    exception);
        }
    }

    private static String value(String envKey, String propertyKey, String defaultValue) {
        String value = System.getenv(envKey);
        if (value == null || value.isBlank()) {
            value = System.getProperty(envKey);
        }
        if (value == null || value.isBlank()) {
            value = PROPERTIES.getProperty(propertyKey);
        }
        return value == null || value.isBlank() ? defaultValue : value;
    }

    private static Properties loadProperties() {
        Properties properties = new Properties();
        try (InputStream input = DatabaseManager.class
                .getResourceAsStream("/database.properties")) {
            if (input != null) {
                try (InputStreamReader reader = new InputStreamReader(input, StandardCharsets.UTF_8)) {
                    properties.load(reader);
                }
            }
            return properties;
        } catch (IOException exception) {
            throw new DatabaseException("Не удалось прочитать database.properties.", exception);
        }
    }
}
