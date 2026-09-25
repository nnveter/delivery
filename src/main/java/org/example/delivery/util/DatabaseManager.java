package org.example.delivery.util;

import org.example.delivery.exception.DatabaseException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

public final class DatabaseManager {
    private static final String DEFAULT_URL = "jdbc:postgresql://localhost:5432/delivery_db";
    private static final String DEFAULT_USER = "postgres";
    private static final String DEFAULT_PASSWORD = "";
    private static final Map<String, String> DOT_ENV = loadDotEnv();

    private DatabaseManager() {
    }

    public static Connection getConnection() {
        String url = environmentOrDefault("DB_URL", DEFAULT_URL);
        String user = environmentOrDefault("DB_USER", DEFAULT_USER);
        String password = environmentOrDefault("DB_PASSWORD", DEFAULT_PASSWORD);
        try {
            return DriverManager.getConnection(url, user, password);
        } catch (SQLException exception) {
            throw new DatabaseException(
                    "Не удалось подключиться к базе данных. Проверьте DB_URL, DB_USER и DB_PASSWORD.",
                    exception);
        }
    }

    private static String environmentOrDefault(String name, String defaultValue) {
        String value = System.getenv(name);
        if (value != null && !value.isBlank()) {
            return value;
        }
        value = System.getProperty(name);
        if (value != null && !value.isBlank()) {
            return value;
        }
        value = DOT_ENV.get(name);
        return value == null || value.isBlank() ? defaultValue : value;
    }

    private static Map<String, String> loadDotEnv() {
        Path envPath = Path.of(System.getProperty("user.dir"), ".env");
        if (!Files.isRegularFile(envPath)) {
            return Map.of();
        }

        Map<String, String> values = new HashMap<>();
        try {
            for (String line : Files.readAllLines(envPath, StandardCharsets.UTF_8)) {
                String trimmed = line.trim();
                if (trimmed.isEmpty() || trimmed.startsWith("#") || !trimmed.contains("=")) {
                    continue;
                }
                int separator = trimmed.indexOf('=');
                String key = trimmed.substring(0, separator).trim();
                String value = trimmed.substring(separator + 1).trim();
                if (value.length() >= 2
                        && ((value.startsWith("\"") && value.endsWith("\""))
                        || (value.startsWith("'") && value.endsWith("'")))) {
                    value = value.substring(1, value.length() - 1);
                }
                values.put(key, value);
            }
            return Map.copyOf(values);
        } catch (IOException exception) {
            throw new DatabaseException("Не удалось прочитать файл .env.", exception);
        }
    }
}
