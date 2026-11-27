package org.example.delivery.domain;

/** Вычисляемый приоритет обработки заказа. Рассчитывается только на сервере. */
public enum PriorityLevel {
    /** Заказ завершён (доставлен или отменён) — приоритет не применяется. */
    NOT_APPLICABLE,
    LOW,
    NORMAL,
    HIGH,
    URGENT
}
