package org.example.delivery.service;

import org.example.delivery.domain.DeliveryStatus;
import org.example.delivery.domain.PriorityLevel;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * Единообразно рассчитывает приоритет заказа для всех клиентов (web, JavaFX, Android).
 *
 * <p>Правило (баллы складываются):</p>
 * <ul>
 *     <li>хрупкий груз — +2;</li>
 *     <li>стоимость от 1500 руб. — +2, от 800 руб. — +1;</li>
 *     <li>вес от 5 кг — +1;</li>
 *     <li>посылка уже у курьера (PICKED_UP, IN_TRANSIT) — +1.</li>
 * </ul>
 * <p>4 и более баллов — URGENT, 3 — HIGH, 1–2 — NORMAL, 0 — LOW.
 * Для завершённых заказов (DELIVERED, CANCELLED) приоритет не применяется.</p>
 */
@Component
public class PriorityCalculator {
    static final BigDecimal EXPENSIVE = new BigDecimal("1500.00");
    static final BigDecimal MEDIUM_PRICE = new BigDecimal("800.00");
    static final BigDecimal HEAVY = new BigDecimal("5.00");

    public PriorityLevel calculate(DeliveryStatus status, BigDecimal price, BigDecimal weight, boolean fragile) {
        if (status != null && status.isFinal()) {
            return PriorityLevel.NOT_APPLICABLE;
        }
        int score = 0;
        if (fragile) score += 2;
        if (price != null && price.compareTo(EXPENSIVE) >= 0) score += 2;
        else if (price != null && price.compareTo(MEDIUM_PRICE) >= 0) score += 1;
        if (weight != null && weight.compareTo(HEAVY) >= 0) score += 1;
        if (status == DeliveryStatus.PICKED_UP || status == DeliveryStatus.IN_TRANSIT) score += 1;

        if (score >= 4) return PriorityLevel.URGENT;
        if (score == 3) return PriorityLevel.HIGH;
        if (score >= 1) return PriorityLevel.NORMAL;
        return PriorityLevel.LOW;
    }
}
