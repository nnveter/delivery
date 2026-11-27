package org.example.delivery.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** Бизнес-правило КР №1: допустимые переходы статусов заказа. */
class DeliveryStatusTest {

    @Test
    void orderMovesForwardStepByStep() {
        assertThat(DeliveryStatus.CREATED.canTransitionTo(DeliveryStatus.CONFIRMED)).isTrue();
        assertThat(DeliveryStatus.PICKED_UP.canTransitionTo(DeliveryStatus.IN_TRANSIT)).isTrue();
        assertThat(DeliveryStatus.CREATED.canTransitionTo(DeliveryStatus.DELIVERED)).isFalse();
    }

    @Test
    void cannotCancelAfterPickUpOrChangeFinishedOrder() {
        assertThat(DeliveryStatus.ASSIGNED_TO_COURIER.canTransitionTo(DeliveryStatus.CANCELLED)).isTrue();
        assertThat(DeliveryStatus.PICKED_UP.canTransitionTo(DeliveryStatus.CANCELLED)).isFalse();
        assertThat(DeliveryStatus.DELIVERED.canTransitionTo(DeliveryStatus.CANCELLED)).isFalse();
        assertThat(DeliveryStatus.CANCELLED.canTransitionTo(DeliveryStatus.CREATED)).isFalse();
    }
}
