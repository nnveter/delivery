package org.example.delivery.service;

import org.example.delivery.domain.DeliveryStatus;
import org.example.delivery.domain.PriorityLevel;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/** Фиксирует бизнес-правило расчёта приоритета заказа. */
class PriorityCalculatorTest {
    private final PriorityCalculator calculator = new PriorityCalculator();

    @Test
    void cheapLightRegularOrderIsLow() {
        assertThat(calculator.calculate(DeliveryStatus.CREATED, new BigDecimal("450.00"), new BigDecimal("0.50"), false))
                .isEqualTo(PriorityLevel.LOW);
    }

    @Test
    void mediumPriceIsNormal() {
        assertThat(calculator.calculate(DeliveryStatus.CONFIRMED, new BigDecimal("900.00"), new BigDecimal("2.50"), false))
                .isEqualTo(PriorityLevel.NORMAL);
    }

    @Test
    void fragileWithMediumPriceIsHigh() {
        assertThat(calculator.calculate(DeliveryStatus.CREATED, new BigDecimal("800.00"), new BigDecimal("1.00"), true))
                .isEqualTo(PriorityLevel.HIGH);
    }

    @Test
    void fragileExpensiveOrderIsUrgent() {
        assertThat(calculator.calculate(DeliveryStatus.CREATED, new BigDecimal("1600.00"), new BigDecimal("1.00"), true))
                .isEqualTo(PriorityLevel.URGENT);
    }

    @Test
    void parcelOnTheWayGetsExtraPoint() {
        assertThat(calculator.calculate(DeliveryStatus.IN_TRANSIT, new BigDecimal("1500.00"), new BigDecimal("1.00"), false))
                .isEqualTo(PriorityLevel.HIGH);
    }

    @Test
    void finishedOrderHasNoPriority() {
        assertThat(calculator.calculate(DeliveryStatus.DELIVERED, new BigDecimal("5000.00"), new BigDecimal("50.00"), true))
                .isEqualTo(PriorityLevel.NOT_APPLICABLE);
        assertThat(calculator.calculate(DeliveryStatus.CANCELLED, new BigDecimal("5000.00"), new BigDecimal("50.00"), true))
                .isEqualTo(PriorityLevel.NOT_APPLICABLE);
    }
}
