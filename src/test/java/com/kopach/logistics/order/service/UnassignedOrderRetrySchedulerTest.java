package com.kopach.logistics.order.service;

import com.kopach.logistics.order.dto.OrderCreatedEvent;
import com.kopach.logistics.order.entity.Order;
import com.kopach.logistics.order.entity.OrderStatus;
import com.kopach.logistics.order.repository.OrderRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UnassignedOrderRetrySchedulerTest {

    private final OrderRepository orderRepository = mock(OrderRepository.class);
    private final OrderEventPublisher eventPublisher = mock(OrderEventPublisher.class);
    private final UnassignedOrderRetryScheduler scheduler =
            new UnassignedOrderRetryScheduler(orderRepository, eventPublisher, Duration.ofSeconds(30));

    @Test
    void ordersStillNew_shouldGetOrderCreatedAgain() {
        when(orderRepository.findTop50ByStatusAndCreatedAtBeforeOrderByCreatedAtAsc(eq(OrderStatus.NEW), any()))
                .thenReturn(List.of(order(1L), order(2L)));

        scheduler.republishUnassignedOrders();

        ArgumentCaptor<OrderCreatedEvent> events = ArgumentCaptor.forClass(OrderCreatedEvent.class);
        verify(eventPublisher, times(2)).publishOrderCreated(events.capture());
        assertThat(events.getAllValues()).extracting(OrderCreatedEvent::getOrderId).containsExactly(1L, 2L);
    }

    @Test
    void noWaitingOrders_shouldPublishNothing() {
        when(orderRepository.findTop50ByStatusAndCreatedAtBeforeOrderByCreatedAtAsc(eq(OrderStatus.NEW), any()))
                .thenReturn(List.of());

        scheduler.republishUnassignedOrders();

        verify(eventPublisher, never()).publishOrderCreated(any());
    }

    private Order order(Long id) {
        Order order = new Order();
        order.setId(id);
        order.setClientId(10L);
        order.setStatus(OrderStatus.NEW);
        order.setFromAddress("Minsk");
        order.setToAddress("Grodno");
        order.setPrice(BigDecimal.valueOf(300));
        order.setCreatedAt(LocalDateTime.now().minusMinutes(5));
        return order;
    }
}
