package org.example.delivery.service;

import org.example.delivery.exception.BusinessException;
import org.example.delivery.exception.EntityNotFoundException;
import org.example.delivery.model.DeliveryOrder;
import org.example.delivery.model.DeliveryStatus;
import org.example.delivery.repository.ClientRepository;
import org.example.delivery.repository.DeliveryOrderRepository;

import java.math.BigDecimal;
import java.util.List;

public final class DeliveryOrderService {
    private final DeliveryOrderRepository orderRepository;
    private final ClientRepository clientRepository;

    public DeliveryOrderService(
            DeliveryOrderRepository orderRepository,
            ClientRepository clientRepository) {
        this.orderRepository = orderRepository;
        this.clientRepository = clientRepository;
    }

    public DeliveryOrder create(
            long clientId,
            String pickupAddress,
            String deliveryAddress,
            String description,
            BigDecimal weight,
            BigDecimal price) {
        ensureClientExists(clientId);
        ensureDifferentAddresses(pickupAddress, deliveryAddress);
        return orderRepository.save(new DeliveryOrder(
                clientId, pickupAddress, deliveryAddress, description, weight, price));
    }

    public List<DeliveryOrder> findAll() {
        return orderRepository.findAll();
    }

    public DeliveryOrder findById(long id) {
        return orderRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Заказ с ID " + id + " не найден."));
    }

    public DeliveryOrder update(
            long id,
            long clientId,
            String pickupAddress,
            String deliveryAddress,
            String description,
            BigDecimal weight,
            BigDecimal price) {
        DeliveryOrder current = findById(id);
        ensureClientExists(clientId);
        ensureDifferentAddresses(pickupAddress, deliveryAddress);
        DeliveryOrder updated = new DeliveryOrder(
                id, clientId, null, pickupAddress, deliveryAddress,
                description, weight, price, current.getCreatedAt(), current.getStatus());
        orderRepository.update(updated);
        return findById(id);
    }

    public void delete(long id) {
        DeliveryOrder order = findById(id);
        if (order.getStatus() == DeliveryStatus.IN_TRANSIT
                || order.getStatus() == DeliveryStatus.DELIVERED) {
            throw new BusinessException("Нельзя удалить заказ в статусе " + order.getStatus() + ".");
        }
        orderRepository.deleteById(id);
    }

    public DeliveryOrder changeStatus(long id, DeliveryStatus target) {
        orderRepository.changeStatus(id, target);
        return findById(id);
    }

    public List<DeliveryOrder> search(String query) {
        if (query == null || query.isBlank()) {
            throw new BusinessException("Строка поиска не может быть пустой.");
        }
        return orderRepository.search(query);
    }

    public List<DeliveryOrder> filter(
            DeliveryStatus status,
            BigDecimal minPrice,
            BigDecimal maxPrice) {
        if (minPrice != null && minPrice.signum() < 0
                || maxPrice != null && maxPrice.signum() < 0) {
            throw new BusinessException("Цена не может быть отрицательной.");
        }
        if (minPrice != null && maxPrice != null && minPrice.compareTo(maxPrice) > 0) {
            throw new BusinessException("Минимальная цена не может быть больше максимальной.");
        }
        return orderRepository.filter(status, minPrice, maxPrice);
    }

    public List<DeliveryOrder> sort(String sortBy, boolean descending) {
        return orderRepository.sort(sortBy, descending);
    }

    public DeliveryStatistics statistics() {
        long totalOrders = orderRepository.findAll().size();
        long delivered = orderRepository.countByStatus(DeliveryStatus.DELIVERED);
        long cancelled = orderRepository.countByStatus(DeliveryStatus.CANCELLED);
        return new DeliveryStatistics(
                clientRepository.findAll().size(),
                totalOrders,
                totalOrders - delivered - cancelled,
                delivered,
                cancelled,
                orderRepository.totalRevenue());
    }

    private void ensureClientExists(long clientId) {
        if (clientId <= 0 || clientRepository.findById(clientId).isEmpty()) {
            throw new BusinessException("Клиент с ID " + clientId + " не существует.");
        }
    }

    private void ensureDifferentAddresses(String pickupAddress, String deliveryAddress) {
        if (pickupAddress == null || deliveryAddress == null
                || pickupAddress.isBlank() || deliveryAddress.isBlank()) {
            throw new BusinessException("Оба адреса обязательны.");
        }
        if (pickupAddress.trim().equalsIgnoreCase(deliveryAddress.trim())) {
            throw new BusinessException("Адреса отправления и доставки должны различаться.");
        }
    }
}
