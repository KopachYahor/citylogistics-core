package com.kopach.logistics.order.service;

import com.kopach.logistics.auth.entity.Role;
import com.kopach.logistics.auth.entity.User;
import com.kopach.logistics.auth.repository.UserRepository;
import com.kopach.logistics.common.ForbiddenException;
import com.kopach.logistics.order.dto.OrderStatusChangedEvent;
import com.kopach.logistics.common.NotFoundException;
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
import com.kopach.logistics.order.dto.OrderCreatedEvent;
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

    @Mock
    private UserRepository userRepository;

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
        verify(eventPublisher, times(1)).publishOrderCreated(any(OrderCreatedEvent.class));    }


    @Test
    void cancelOrder_whenNewOrder_shouldChangeStatusToCancelled() {
        // given
        when(orderRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(newOrder));
        when(orderRepository.save(any(Order.class))).thenReturn(newOrder);

        // when
        OrderResponse response = orderService.cancelOrder(1L, 1L);

        // then
        assertThat(response.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        verify(orderRepository, times(1)).save(any(Order.class));
    }


    @Test
    void cancelOrder_whenOrderNotFound_shouldThrowException() {
        // given
        when(orderRepository.findByIdForUpdate(999L)).thenReturn(Optional.empty());

        // when + then
        assertThatThrownBy(() -> orderService.cancelOrder(999L, 1L))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("Order not found");

        verify(orderRepository, never()).save(any(Order.class));
    }


    @Test
    void cancelOrder_whenDifferentClient_shouldThrowAccessDenied() {
        // given
        when(orderRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(newOrder));

        // when + then (clientId=999 — не владелец)
        assertThatThrownBy(() -> orderService.cancelOrder(1L, 999L))
                .isInstanceOf(ForbiddenException.class)
                .hasMessage("Access denied");

        verify(orderRepository, never()).save(any(Order.class));
    }


    @Test
    void cancelOrder_whenAlreadyCancelled_shouldThrowException() {
        // given
        when(orderRepository.findByIdForUpdate(2L)).thenReturn(Optional.of(cancelledOrder));

        // when + then
        assertThatThrownBy(() -> orderService.cancelOrder(2L, 1L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Cannot cancel order in status CANCELLED");

        verify(orderRepository, never()).save(any(Order.class));
    }


    @Test
    void updateStatus_byAssignedDriver_shouldChangeStatusAndPublishEvent() {
        // given: водитель 7 назначен на заказ в статусе ACCEPTED
        when(userRepository.findById(10L)).thenReturn(Optional.of(driverUser(7L)));
        when(orderRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(acceptedOrder(7L)));
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        // when
        OrderResponse response = orderService.updateStatus(1L, OrderStatus.IN_TRANSIT, 10L);

        // then
        assertThat(response.getStatus()).isEqualTo(OrderStatus.IN_TRANSIT);
        verify(eventPublisher).publishOrderStatusChanged(any(OrderStatusChangedEvent.class));
    }

    @Test
    void updateStatus_byAnotherDriver_shouldThrowAccessDenied() {
        // given: заказ назначен водителю 7, а меняет водитель 8
        when(userRepository.findById(10L)).thenReturn(Optional.of(driverUser(8L)));
        when(orderRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(acceptedOrder(7L)));

        // when + then
        assertThatThrownBy(() -> orderService.updateStatus(1L, OrderStatus.IN_TRANSIT, 10L))
                .isInstanceOf(ForbiddenException.class)
                .hasMessage("Access denied");

        verify(orderRepository, never()).save(any(Order.class));
        verify(eventPublisher, never()).publishOrderStatusChanged(any());
    }

    @Test
    void updateStatus_skippingSteps_shouldThrowAndNotSave() {
        // given: ACCEPTED → DELIVERED (пропуск IN_TRANSIT и ARRIVED)
        when(userRepository.findById(10L)).thenReturn(Optional.of(driverUser(7L)));
        when(orderRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(acceptedOrder(7L)));

        // when + then
        assertThatThrownBy(() -> orderService.updateStatus(1L, OrderStatus.DELIVERED, 10L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Cannot transition from ACCEPTED to DELIVERED");

        verify(orderRepository, never()).save(any(Order.class));
    }

    @Test
    void updateStatus_byUserWithoutDriverProfile_shouldThrowForbidden() {
        // given: у пользователя нет driverId
        when(userRepository.findById(10L)).thenReturn(Optional.of(driverUser(null)));

        // when + then
        assertThatThrownBy(() -> orderService.updateStatus(1L, OrderStatus.IN_TRANSIT, 10L))
                .isInstanceOf(ForbiddenException.class)
                .hasMessage("User is not a driver");

        verify(orderRepository, never()).findByIdForUpdate(any());
    }

    private User driverUser(Long driverId) {
        User user = new User("driver@test.local", "hash", Role.DRIVER, LocalDateTime.now());
        user.setId(10L);
        user.setDriverId(driverId);
        return user;
    }

    private Order acceptedOrder(Long driverId) {
        Order order = new Order();
        order.setId(1L);
        order.setClientId(1L);
        order.setDriverId(driverId);
        order.setStatus(OrderStatus.ACCEPTED);
        return order;
    }
}