package com.kopach.logistics.order.service;

import com.kopach.logistics.driver.dto.DriverAssignedEvent;
import com.kopach.logistics.order.dto.OrderStatusChangedEvent;
import com.kopach.logistics.order.entity.Order;
import com.kopach.logistics.order.entity.OrderStatus;
import com.kopach.logistics.order.repository.OrderRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DriverAssignedListenerTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private OrderEventPublisher eventPublisher;

    @InjectMocks
    private DriverAssignedListener listener;

    @Test
    void newOrder_shouldGetDriverAndBecomeAccepted() {
        Order order = order(OrderStatus.NEW);
        when(orderRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(order));

        listener.onDriverAssigned(new DriverAssignedEvent(1L, 5L));

        assertThat(order.getStatus()).isEqualTo(OrderStatus.ACCEPTED);
        assertThat(order.getDriverId()).isEqualTo(5L);
        verify(orderRepository).save(order);
    }

    @Test
    void cancelledOrder_shouldStayCancelledAndReleaseDriver() {
        // клиент отменил заказ раньше, чем пришло назначение
        Order order = order(OrderStatus.CANCELLED);
        when(orderRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(order));

        listener.onDriverAssigned(new DriverAssignedEvent(1L, 5L));

        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        verify(orderRepository, never()).save(any());

        // повторное событие об отмене — уже с водителем, чтобы driver-service его освободил
        ArgumentCaptor<OrderStatusChangedEvent> captor = ArgumentCaptor.forClass(OrderStatusChangedEvent.class);
        verify(eventPublisher).publishOrderStatusChanged(captor.capture());
        assertThat(captor.getValue().getDriverId()).isEqualTo(5L);
        assertThat(captor.getValue().getNewStatus()).isEqualTo("CANCELLED");
    }

    @Test
    void repeatedEventForAcceptedOrder_shouldBeIgnored() {
        // Kafka доставила то же событие второй раз (at-least-once)
        Order order = order(OrderStatus.ACCEPTED);
        order.setDriverId(5L);
        when(orderRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(order));

        listener.onDriverAssigned(new DriverAssignedEvent(1L, 5L));

        verify(orderRepository, never()).save(any());
        verify(eventPublisher, never()).publishOrderStatusChanged(any());
    }

    private Order order(OrderStatus status) {
        Order order = new Order();
        order.setId(1L);
        order.setClientId(1L);
        order.setStatus(status);
        return order;
    }
}
