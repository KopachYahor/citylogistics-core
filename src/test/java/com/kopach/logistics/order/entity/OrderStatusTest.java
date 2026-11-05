package com.kopach.logistics.order.entity;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit-тесты конечного автомата статусов: Spring и база не нужны, это обычный enum.
 */
class OrderStatusTest {

    @Test
    void canTransitionTo_allowsOnlyNextStepOfLifecycle() {
        assertThat(OrderStatus.NEW.canTransitionTo(OrderStatus.ACCEPTED)).isTrue();
        assertThat(OrderStatus.ACCEPTED.canTransitionTo(OrderStatus.IN_TRANSIT)).isTrue();
        assertThat(OrderStatus.IN_TRANSIT.canTransitionTo(OrderStatus.ARRIVED)).isTrue();
        assertThat(OrderStatus.ARRIVED.canTransitionTo(OrderStatus.DELIVERED)).isTrue();
    }

    @Test
    void canTransitionTo_forbidsSkippingSteps() {
        assertThat(OrderStatus.ACCEPTED.canTransitionTo(OrderStatus.DELIVERED)).isFalse();
        assertThat(OrderStatus.NEW.canTransitionTo(OrderStatus.IN_TRANSIT)).isFalse();
    }

    @Test
    void canTransitionTo_forbidsGoingBackAndStayingInPlace() {
        assertThat(OrderStatus.ARRIVED.canTransitionTo(OrderStatus.IN_TRANSIT)).isFalse();
        assertThat(OrderStatus.ACCEPTED.canTransitionTo(OrderStatus.ACCEPTED)).isFalse();
    }

    @Test
    void canTransitionTo_finalStatusesHaveNoNextStep() {
        for (OrderStatus target : OrderStatus.values()) {
            assertThat(OrderStatus.DELIVERED.canTransitionTo(target)).isFalse();
            assertThat(OrderStatus.CANCELLED.canTransitionTo(target)).isFalse();
        }
    }
}
