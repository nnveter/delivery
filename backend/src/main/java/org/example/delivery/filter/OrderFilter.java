package org.example.delivery.filter;

import org.example.delivery.domain.DeliveryStatus;
import org.example.delivery.domain.PriorityLevel;

import java.math.BigDecimal;
import java.util.List;

/** Результат безопасного разбора строки умного фильтра заказов. */
public record OrderFilter(
        String freeText,
        String client,
        DeliveryStatus status,
        Boolean fragile,
        PriorityLevel priority,
        String tag,
        Boolean archived,
        List<NumberConstraint> numbers) {

    public enum Operator { LT, LTE, EQ, GTE, GT }

    /** Числовое поле, по которому разрешено сравнение. */
    public enum NumericField {
        PRICE("price"), WEIGHT("weight");

        private final String attribute;

        NumericField(String attribute) { this.attribute = attribute; }

        /** Имя атрибута JPA-сущности. */
        public String attribute() { return attribute; }
    }

    public record NumberConstraint(NumericField field, Operator operator, BigDecimal value) { }
}
