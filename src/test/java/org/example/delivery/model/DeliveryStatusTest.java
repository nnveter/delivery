package org.example.delivery.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DeliveryStatusTest {
    @Test
    void allowsOnlySequentialTransitions() {
        assertTrue(DeliveryStatus.CREATED.canTransitionTo(DeliveryStatus.CONFIRMED));
        assertTrue(DeliveryStatus.CONFIRMED.canTransitionTo(DeliveryStatus.ASSIGNED_TO_COURIER));
        assertFalse(DeliveryStatus.CREATED.canTransitionTo(DeliveryStatus.DELIVERED));
        assertFalse(DeliveryStatus.DELIVERED.canTransitionTo(DeliveryStatus.CANCELLED));
    }
}
