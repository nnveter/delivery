package org.example.delivery.service;

import java.math.BigDecimal;

public record DeliveryStatistics(
        long totalClients,
        long totalOrders,
        long activeOrders,
        long deliveredOrders,
        long cancelledOrders,
        BigDecimal deliveredRevenue) {
}
