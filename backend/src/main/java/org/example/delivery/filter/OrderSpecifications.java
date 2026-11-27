package org.example.delivery.filter;

import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import org.example.delivery.domain.Client;
import org.example.delivery.domain.DeliveryOrder;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Создаёт параметризованную JPA Specification из разобранного фильтра. */
public final class OrderSpecifications {
    private OrderSpecifications() { }

    public static Specification<DeliveryOrder> from(OrderFilter filter) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (filter.archived() != null) predicates.add(cb.equal(root.get("archived"), filter.archived()));
            if (filter.status() != null) predicates.add(cb.equal(root.get("status"), filter.status()));
            if (filter.fragile() != null) predicates.add(cb.equal(root.get("fragile"), filter.fragile()));
            if (filter.priority() != null) predicates.add(cb.equal(root.get("priority"), filter.priority()));

            Join<DeliveryOrder, Client> client = root.join("client", JoinType.LEFT);
            if (filter.client() != null) {
                String like = "%" + escape(filter.client()) + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(client.get("fullName")), like, '\\'),
                        cb.like(cb.lower(client.get("phone")), like, '\\')));
            }
            if (filter.tag() != null) {
                // Подзапрос вместо join, чтобы не размножать строки и не требовать DISTINCT.
                var sub = query.subquery(Integer.class);
                var subRoot = sub.from(DeliveryOrder.class);
                Join<DeliveryOrder, String> tags = subRoot.joinSet("tags");
                sub.select(cb.literal(1)).where(
                        cb.equal(subRoot.get("id"), root.get("id")),
                        cb.equal(cb.lower(tags), filter.tag().toLowerCase(Locale.ROOT)));
                predicates.add(cb.exists(sub));
            }
            if (filter.freeText() != null && !filter.freeText().isBlank()) {
                String like = "%" + escape(filter.freeText()) + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("orderNumber")), like, '\\'),
                        cb.like(cb.lower(root.get("description")), like, '\\'),
                        cb.like(cb.lower(root.get("pickupAddress")), like, '\\'),
                        cb.like(cb.lower(root.get("deliveryAddress")), like, '\\'),
                        cb.like(cb.lower(client.get("fullName")), like, '\\'),
                        cb.like(cb.lower(client.get("phone")), like, '\\')));
            }
            for (OrderFilter.NumberConstraint c : filter.numbers()) {
                Expression<BigDecimal> field = root.get(c.field().attribute());
                predicates.add(switch (c.operator()) {
                    case LT -> cb.lessThan(field, c.value());
                    case LTE -> cb.lessThanOrEqualTo(field, c.value());
                    case EQ -> cb.equal(field, c.value());
                    case GTE -> cb.greaterThanOrEqualTo(field, c.value());
                    case GT -> cb.greaterThan(field, c.value());
                });
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }

    /** Экранирует спецсимволы LIKE, чтобы пользовательский ввод не стал шаблоном. */
    static String escape(String value) {
        return value.toLowerCase(Locale.ROOT).replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
