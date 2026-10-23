package org.example.delivery.service;

import java.math.BigDecimal;

/**
 * Статистика системы доставки (КР №2).
 * Содержит 11 показателей — больше обязательного минимума в 5.
 */
public record DeliveryStatistics(
        long totalClients,
        long totalOrders,
        long activeOrders,
        long deliveredOrders,
        long cancelledOrders,
        BigDecimal deliveredRevenue,
        BigDecimal averageOrderPrice,
        BigDecimal averageOrderWeight,
        BigDecimal minOrderPrice,
        BigDecimal maxOrderPrice,
        double deliveredSharePercent) {
}
