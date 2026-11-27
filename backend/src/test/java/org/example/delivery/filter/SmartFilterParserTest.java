package org.example.delivery.filter;

import org.example.delivery.domain.DeliveryStatus;
import org.example.delivery.domain.PriorityLevel;
import org.example.delivery.error.BadRequestException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Проверяет разбор умного фильтра заказов. */
class SmartFilterParserTest {
    private final SmartFilterParser parser = new SmartFilterParser();

    @Test
    void emptyQueryShowsOnlyActiveOrders() {
        OrderFilter result = parser.parse("  ");
        assertThat(result.archived()).isFalse();
        assertThat(result.freeText()).isEmpty();
        assertThat(result.numbers()).isEmpty();
    }

    @Test
    void parsesMixedQuery() {
        OrderFilter result = parser.parse("Книга клиент:Иванов статус:в_пути хрупкий:да цена>=500 вес<3,5 приоритет:высокий тег:срочно");
        assertThat(result.freeText()).isEqualTo("Книга");
        assertThat(result.client()).isEqualTo("Иванов");
        assertThat(result.status()).isEqualTo(DeliveryStatus.IN_TRANSIT);
        assertThat(result.fragile()).isTrue();
        assertThat(result.priority()).isEqualTo(PriorityLevel.HIGH);
        assertThat(result.tag()).isEqualTo("срочно");
        assertThat(result.numbers()).containsExactly(
                new OrderFilter.NumberConstraint(OrderFilter.NumericField.PRICE, OrderFilter.Operator.GTE, new BigDecimal("500")),
                new OrderFilter.NumberConstraint(OrderFilter.NumericField.WEIGHT, OrderFilter.Operator.LT, new BigDecimal("3.5")));
    }

    @Test
    void supportsQuotedTextEnglishKeysAndAllArchive() {
        OrderFilter result = parser.parse("\"ул. Мира\" status:delivered archived:all");
        assertThat(result.freeText()).isEqualTo("ул. Мира");
        assertThat(result.status()).isEqualTo(DeliveryStatus.DELIVERED);
        assertThat(result.archived()).isNull();
    }

    @Test
    void sqlLikeInputStaysPlainText() {
        OrderFilter result = parser.parse("'; DROP TABLE delivery_orders; --");
        assertThat(result.freeText()).isEqualTo("'; DROP TABLE delivery_orders; --");
        assertThat(OrderSpecifications.escape("100%_off")).isEqualTo("100\\%\\_off");
    }

    @Test
    void rejectsUnknownStatus() {
        assertThatThrownBy(() -> parser.parse("статус:потерян")).isInstanceOf(BadRequestException.class);
    }

    @Test
    void rejectsTooLongQuery() {
        assertThatThrownBy(() -> parser.parse("а".repeat(501))).isInstanceOf(BadRequestException.class);
    }
}
