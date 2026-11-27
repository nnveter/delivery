package org.example.delivery.service;

import org.example.delivery.domain.Client;
import org.example.delivery.domain.DeliveryOrder;
import org.example.delivery.dto.OrderDtos.OrderResponse;
import org.springframework.stereotype.Component;

import java.util.Set;

/** Преобразует entity в публичный DTO внутри активной транзакции. */
@Component
public class OrderMapper {
    public OrderResponse toResponse(DeliveryOrder order) {
        Client client = order.getClient();
        return new OrderResponse(order.getId(), order.getOrderNumber(),
                client == null ? null : client.getId(),
                client == null ? null : client.getFullName(),
                client == null ? null : client.getPhone(),
                order.getPickupAddress(), order.getDeliveryAddress(), order.getDescription(),
                order.getWeight(), order.getPrice(), order.isFragile(),
                order.getStatus(), order.getPriority(),
                order.isArchived(), order.getArchivedAt(), Set.copyOf(order.getTags()),
                order.getCreatedAt(), order.getUpdatedAt(), order.getVersion());
    }
}
