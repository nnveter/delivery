package org.example.delivery.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.example.delivery.domain.DeliveryStatus;
import org.example.delivery.domain.PriorityLevel;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** DTO-контракты заказов доставки и массовых операций. */
public final class OrderDtos {
    private OrderDtos() {
    }

    /**
     * Входная модель создания и изменения заказа.
     *
     * <p>Номер заказа можно не передавать при создании — сервер сгенерирует его сам.
     * Приоритет не принимается от клиента: он всегда вычисляется на сервере.</p>
     */
    public record OrderRequest(
            @Size(max = 40)
            @Pattern(regexp = "^[A-Za-z0-9-]+$", message = "допустимы латинские буквы, цифры и дефис")
            String orderNumber,
            @NotNull UUID clientId,
            @NotBlank @Size(max = 500) String pickupAddress,
            @NotBlank @Size(max = 500) String deliveryAddress,
            @Size(max = 2000) String description,
            @NotNull @DecimalMin(value = "0.01") @DecimalMax("10000.00") @Digits(integer = 8, fraction = 2) BigDecimal weight,
            @NotNull @DecimalMin(value = "0.01") @DecimalMax("10000000.00") @Digits(integer = 8, fraction = 2) BigDecimal price,
            boolean fragile,
            @NotNull DeliveryStatus status,
            @Size(max = 20) Set<@NotBlank @Size(max = 50) String> tags,
            Long version) {
    }

    /** Выходная карточка заказа. */
    public record OrderResponse(
            UUID id,
            String orderNumber,
            UUID clientId,
            String clientName,
            String clientPhone,
            String pickupAddress,
            String deliveryAddress,
            String description,
            BigDecimal weight,
            BigDecimal price,
            boolean fragile,
            DeliveryStatus status,
            PriorityLevel priority,
            boolean archived,
            Instant archivedAt,
            Set<String> tags,
            Instant createdAt,
            Instant updatedAt,
            long version) {
    }

    /** Поддерживаемые массовые действия. */
    public enum BulkAction { ARCHIVE, RESTORE, SET_STATUS, ADD_TAG, REMOVE_TAG }

    /** Запрос массового изменения. */
    public record BulkRequest(
            @NotNull @Size(min = 1, max = 500) Set<UUID> ids,
            @NotNull BulkAction action,
            DeliveryStatus status,
            @Size(max = 50) String tag) {
    }

    /** Результат массовой операции с явным списком ошибок. */
    public record BulkResult(int requested, int succeeded, List<BulkFailure> failures) {
    }

    /** Ошибка отдельной записи в массовой операции. */
    public record BulkFailure(UUID id, String message) {
    }
}
