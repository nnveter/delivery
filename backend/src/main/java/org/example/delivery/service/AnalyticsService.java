package org.example.delivery.service;

import org.example.delivery.dto.AnalyticsDtos.AnalyticsResponse;
import org.example.delivery.dto.AnalyticsDtos.NamedCount;
import org.example.delivery.repository.DeliveryOrderRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/** Формирует аналитическую сводку агрегатными запросами на стороне PostgreSQL. */
@Service
public class AnalyticsService {
    private final DeliveryOrderRepository orders;

    public AnalyticsService(DeliveryOrderRepository orders) {
        this.orders = orders;
    }

    @Transactional(readOnly = true)
    public AnalyticsResponse summary() {
        return new AnalyticsResponse(
                orders.count(),
                orders.countByArchivedFalse(),
                orders.countByArchivedTrue(),
                orders.countByArchivedFalseAndFragileTrue(),
                round(orders.averagePriceForActive()),
                round(orders.averageWeightForActive()),
                map(orders.countActiveByClient()),
                map(orders.countActiveByStatus()),
                map(orders.countActiveByPriority()));
    }

    private static BigDecimal round(Double value) {
        return value == null ? BigDecimal.ZERO.setScale(2) : BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP);
    }

    private static List<NamedCount> map(List<Object[]> rows) {
        return rows.stream().map(row -> new NamedCount(String.valueOf(row[0]), ((Number) row[1]).longValue())).toList();
    }
}
