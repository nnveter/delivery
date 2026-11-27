package org.example.delivery.domain;

/**
 * Статус заказа доставки (перенесён из КР №1 без изменений).
 *
 * <p>Переходы между статусами ограничены: заказ движется только «вперёд» по жизненному
 * циклу, а отменить его можно лишь до того, как курьер забрал посылку.</p>
 */
public enum DeliveryStatus {
    CREATED,
    CONFIRMED,
    ASSIGNED_TO_COURIER,
    PICKED_UP,
    IN_TRANSIT,
    DELIVERED,
    CANCELLED;

    /** Проверяет допустимость перехода в целевой статус. */
    public boolean canTransitionTo(DeliveryStatus target) {
        if (target == null || this == target) {
            return false;
        }
        return switch (this) {
            case CREATED -> target == CONFIRMED || target == CANCELLED;
            case CONFIRMED -> target == ASSIGNED_TO_COURIER || target == CANCELLED;
            case ASSIGNED_TO_COURIER -> target == PICKED_UP || target == CANCELLED;
            case PICKED_UP -> target == IN_TRANSIT;
            case IN_TRANSIT -> target == DELIVERED;
            case DELIVERED, CANCELLED -> false;
        };
    }

    /** Завершённый заказ больше не обрабатывается. */
    public boolean isFinal() {
        return this == DELIVERED || this == CANCELLED;
    }
}
