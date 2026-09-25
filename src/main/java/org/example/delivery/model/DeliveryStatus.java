package org.example.delivery.model;

public enum DeliveryStatus {
    CREATED,
    CONFIRMED,
    ASSIGNED_TO_COURIER,
    PICKED_UP,
    IN_TRANSIT,
    DELIVERED,
    CANCELLED;

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
}
