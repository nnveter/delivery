package org.example.delivery.filter;

import org.example.delivery.domain.DeliveryStatus;
import org.example.delivery.domain.PriorityLevel;
import org.example.delivery.error.BadRequestException;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Разбирает человекочитаемый фильтр в типобезопасную модель.
 *
 * <p>Пример: {@code Книга клиент:Иванов статус:в_пути хрупкий:да цена>=500 вес<3 приоритет:высокий тег:срочно}.</p>
 *
 * <p>Исходная строка фильтра никогда не объединяется с SQL/JPQL. Парсер принимает только
 * известные команды, а Criteria API передаёт значения в запрос как параметры.</p>
 */
@Component
public class SmartFilterParser {
    private static final Pattern TOKEN = Pattern.compile("\"([^\"]*)\"|(\\S+)");
    private static final Pattern NUMBER = Pattern.compile(
            "^(цена|стоимость|price|вес|weight)(<=|>=|=|<|>)([0-9]+(?:[.,][0-9]{1,2})?)$",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

    /** Разбирает строку; по умолчанию показывает только активные (неархивные) заказы. */
    public OrderFilter parse(String source) {
        if (source == null || source.isBlank()) {
            return new OrderFilter("", null, null, null, null, null, false, List.of());
        }
        if (source.length() > 500) throw new BadRequestException("Строка фильтра слишком длинная");

        List<String> free = new ArrayList<>();
        String client = null;
        DeliveryStatus status = null;
        Boolean fragile = null;
        PriorityLevel priority = null;
        String tag = null;
        Boolean archived = false;
        List<OrderFilter.NumberConstraint> numbers = new ArrayList<>();

        Matcher matcher = TOKEN.matcher(source.trim());
        while (matcher.find()) {
            String raw = matcher.group(1) != null ? matcher.group(1) : matcher.group(2);
            String token = raw.trim();
            if (token.isEmpty()) continue;
            String lower = token.toLowerCase(Locale.ROOT);

            Matcher numberMatcher = NUMBER.matcher(lower);
            if (numberMatcher.matches()) {
                numbers.add(new OrderFilter.NumberConstraint(
                        numericField(numberMatcher.group(1)),
                        operator(numberMatcher.group(2)),
                        new BigDecimal(numberMatcher.group(3).replace(',', '.'))));
                continue;
            }

            int colon = token.indexOf(':');
            if (colon > 0) {
                String key = lower.substring(0, colon);
                String value = token.substring(colon + 1).trim();
                String normalized = value.toLowerCase(Locale.ROOT);
                switch (key) {
                    case "клиент", "client" -> client = value;
                    case "статус", "status" -> status = status(normalized);
                    case "хрупкий", "fragile" -> fragile = booleanValue(normalized, "хрупкий");
                    case "приоритет", "priority" -> priority = priority(normalized);
                    case "тег", "tag" -> tag = value;
                    case "архив", "archived" -> archived = archivedValue(normalized);
                    default -> free.add(token);
                }
            } else {
                free.add(token);
            }
        }
        return new OrderFilter(String.join(" ", free), blankToNull(client), status, fragile, priority,
                blankToNull(tag), archived, List.copyOf(numbers));
    }

    private static OrderFilter.NumericField numericField(String value) {
        return switch (value) {
            case "цена", "стоимость", "price" -> OrderFilter.NumericField.PRICE;
            default -> OrderFilter.NumericField.WEIGHT;
        };
    }

    private static OrderFilter.Operator operator(String value) {
        return switch (value) {
            case "<" -> OrderFilter.Operator.LT;
            case "<=" -> OrderFilter.Operator.LTE;
            case "=" -> OrderFilter.Operator.EQ;
            case ">=" -> OrderFilter.Operator.GTE;
            case ">" -> OrderFilter.Operator.GT;
            default -> throw new BadRequestException("Неизвестный оператор сравнения");
        };
    }

    /** Принимает русские и английские названия статусов. */
    public static DeliveryStatus status(String value) {
        return switch (value.toLowerCase(Locale.ROOT).replace('-', '_').replace(' ', '_')) {
            case "создан", "created" -> DeliveryStatus.CREATED;
            case "подтвержден", "подтверждён", "confirmed" -> DeliveryStatus.CONFIRMED;
            case "назначен", "назначен_курьеру", "assigned", "assigned_to_courier" -> DeliveryStatus.ASSIGNED_TO_COURIER;
            case "забран", "получен_курьером", "picked_up" -> DeliveryStatus.PICKED_UP;
            case "в_пути", "in_transit" -> DeliveryStatus.IN_TRANSIT;
            case "доставлен", "delivered" -> DeliveryStatus.DELIVERED;
            case "отменен", "отменён", "cancelled", "canceled" -> DeliveryStatus.CANCELLED;
            default -> throw new BadRequestException("Неизвестный статус: " + value);
        };
    }

    private static PriorityLevel priority(String value) {
        return switch (value) {
            case "не_применяется", "нет", "not_applicable", "n/a" -> PriorityLevel.NOT_APPLICABLE;
            case "низкий", "low" -> PriorityLevel.LOW;
            case "обычный", "normal" -> PriorityLevel.NORMAL;
            case "высокий", "high" -> PriorityLevel.HIGH;
            case "срочный", "urgent" -> PriorityLevel.URGENT;
            default -> throw new BadRequestException("Неизвестный приоритет: " + value);
        };
    }

    private static Boolean booleanValue(String value, String field) {
        return switch (value) {
            case "да", "yes", "true", "1" -> true;
            case "нет", "no", "false", "0" -> false;
            default -> throw new BadRequestException("Поле «" + field + "» принимает да/нет");
        };
    }

    private static Boolean archivedValue(String value) {
        if (value.equals("все") || value.equals("all")) return null;
        return booleanValue(value, "архив");
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
