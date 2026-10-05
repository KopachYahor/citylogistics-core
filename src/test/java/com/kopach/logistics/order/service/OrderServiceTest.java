package com.kopach.logistics.order.service;

import com.kopach.logistics.order.dto.CreateOrderRequest;
import com.kopach.logistics.order.dto.OrderResponse;
import com.kopach.logistics.order.entity.Order;
import com.kopach.logistics.order.entity.OrderStatus;
import com.kopach.logistics.order.repository.OrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private OrderEventPublisher eventPublisher;

    @InjectMocks
    private OrderService orderService;

    private Order newOrder;
    private Order cancelledOrder;

    @BeforeEach
    void setUp() {
        newOrder = new Order();
        newOrder.setId(1L);
        newOrder.setClientId(1L);
        newOrder.setStatus(OrderStatus.NEW);
        newOrder.setFromAddress("Минск");
        newOrder.setToAddress("Москва");
        newOrder.setPrice(BigDecimal.TEN);
        newOrder.setCreatedAt(LocalDateTime.now());
        newOrder.setUpdatedAt(LocalDateTime.now());

        cancelledOrder = new Order();
        cancelledOrder.setId(2L);
        cancelledOrder.setClientId(1L);
        cancelledOrder.setStatus(OrderStatus.CANCELLED);
    }

    // ============ createOrder ============

    @Test
    void createOrder_shouldSaveAndPublishEvent() {
        // given
        CreateOrderRequest request = new CreateOrderRequest("Минск", "Москва", BigDecimal.TEN);
        when(orderRepository.save(any(Order.class))).thenReturn(newOrder);

        // when
        OrderResponse response = orderService.createOrder(request, 1L);

        // then
        assertThat(response).isNotNull();
        assertThat(response.getId()).isEqualTo(1L);
        assertThat(response.getStatus()).isEqualTo(OrderStatus.NEW);

        verify(orderRepository, times(1)).save(any(Order.class));
        verify(eventPublisher, times(1)).publishOrderCreated(1L);
    }

    // ============ cancelOrder — успешная отмена ============

    @Test
    void cancelOrder_whenNewOrder_shouldChangeStatusToCancelled() {
        // given
        when(orderRepository.findById(1L)).thenReturn(Optional.of(newOrder));
        when(orderRepository.save(any(Order.class))).thenReturn(newOrder);

        // when
        OrderResponse response = orderService.cancelOrder(1L, 1L);

        // then
        assertThat(response.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        verify(orderRepository, times(1)).save(any(Order.class));
    }

    // ============ cancelOrder — заказ не найден ============

    @Test
    void cancelOrder_whenOrderNotFound_shouldThrowException() {
        // given
        when(orderRepository.findById(999L)).thenReturn(Optional.empty());

        // when + then
        assertThatThrownBy(() -> orderService.cancelOrder(999L, 1L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Order not found");

        verify(orderRepository, never()).save(any(Order.class));
    }

    // ============ cancelOrder — чужой заказ ============

    @Test
    void cancelOrder_whenDifferentClient_shouldThrowAccessDenied() {
        // given
        when(orderRepository.findById(1L)).thenReturn(Optional.of(newOrder));

        // when + then (clientId=999 — не владелец)
        assertThatThrownBy(() -> orderService.cancelOrder(1L, 999L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Access denied");

        verify(orderRepository, never()).save(any(Order.class));
    }

    // ============ cancelOrder — уже отменён ============

    @Test
    void cancelOrder_whenAlreadyCancelled_shouldThrowException() {
        // given
        when(orderRepository.findById(2L)).thenReturn(Optional.of(cancelledOrder));

        // when + then
        assertThatThrownBy(() -> orderService.cancelOrder(2L, 1L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Cannot cancel order in status CANCELLED");

        verify(orderRepository, never()).save(any(Order.class));
    }
}