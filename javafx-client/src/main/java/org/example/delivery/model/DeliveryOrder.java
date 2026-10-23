package org.example.delivery.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public final class DeliveryOrder {
    private Long id;
    private final Long clientId;
    private final String clientName;
    private final String pickupAddress;
    private final String deliveryAddress;
    private final String description;
    private final BigDecimal weight;
    private final BigDecimal price;
    private final LocalDateTime createdAt;
    private DeliveryStatus status;

    public DeliveryOrder(
            Long id,
            Long clientId,
            String clientName,
            String pickupAddress,
            String deliveryAddress,
            String description,
            BigDecimal weight,
            BigDecimal price,
            LocalDateTime createdAt,
            DeliveryStatus status) {
        this.id = id;
        this.clientId = clientId;
        this.clientName = clientName;
        this.pickupAddress = requireText(pickupAddress, "Адрес отправления");
        this.deliveryAddress = requireText(deliveryAddress, "Адрес доставки");
        this.description = description == null ? "" : description.trim();
        this.weight = requirePositive(weight, "Вес");
        this.price = requirePositive(price, "Стоимость");
        this.createdAt = createdAt == null ? LocalDateTime.now() : createdAt;
        this.status = status == null ? DeliveryStatus.CREATED : status;
    }

    public DeliveryOrder(
            Long clientId,
            String pickupAddress,
            String deliveryAddress,
            String description,
            BigDecimal weight,
            BigDecimal price) {
        this(null, clientId, null, pickupAddress, deliveryAddress, description,
                weight, price, LocalDateTime.now(), DeliveryStatus.CREATED);
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getClientId() {
        return clientId;
    }

    public String getClientName() {
        return clientName;
    }

    public String getPickupAddress() {
        return pickupAddress;
    }

    public String getDeliveryAddress() {
        return deliveryAddress;
    }

    public String getDescription() {
        return description;
    }

    public BigDecimal getWeight() {
        return weight;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public DeliveryStatus getStatus() {
        return status;
    }

    public void changeStatus(DeliveryStatus target) {
        if (!status.canTransitionTo(target)) {
            throw new IllegalStateException(
                    "Недопустимый переход статуса: %s -> %s".formatted(status, target));
        }
        status = target;
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " не может быть пустым");
        }
        return value.trim();
    }

    private static BigDecimal requirePositive(BigDecimal value, String fieldName) {
        if (value == null || value.signum() <= 0) {
            throw new IllegalArgumentException(fieldName + " должен быть больше нуля");
        }
        return value;
    }

    @Override
    public String toString() {
        return "Заказ #%d | клиент: %s | %s -> %s | %.2f кг | %.2f руб. | %s | %s"
                .formatted(id, clientName == null ? clientId : clientName, pickupAddress,
                        deliveryAddress, weight, price, status, createdAt);
    }
}
