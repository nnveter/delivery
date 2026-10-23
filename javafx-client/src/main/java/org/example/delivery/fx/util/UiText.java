package org.example.delivery.fx.util;

import org.example.delivery.model.DeliveryStatus;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/** Вспомогательные преобразования данных для отображения в интерфейсе. */
public final class UiText {
    public static final DateTimeFormatter DATE_TIME =
            DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");

    private UiText() {
    }

    public static String statusName(DeliveryStatus status) {
        if (status == null) {
            return "";
        }
        return switch (status) {
            case CREATED -> "Создан";
            case CONFIRMED -> "Подтверждён";
            case ASSIGNED_TO_COURIER -> "Назначен курьер";
            case PICKED_UP -> "Забран курьером";
            case IN_TRANSIT -> "В пути";
            case DELIVERED -> "Доставлен";
            case CANCELLED -> "Отменён";
        };
    }

    public static String money(java.math.BigDecimal value) {
        return value == null ? "" : String.format("%,.2f руб.", value);
    }

    public static String weight(java.math.BigDecimal value) {
        return value == null ? "" : String.format("%,.2f кг", value);
    }

    public static String dateTime(LocalDateTime value) {
        return value == null ? "" : value.format(DATE_TIME);
    }
}
