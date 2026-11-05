package com.kopach.logistics.order.service;

import com.kopach.logistics.auth.entity.User;
import com.kopach.logistics.auth.repository.UserRepository;
import com.kopach.logistics.common.ForbiddenException;
import com.kopach.logistics.common.NotFoundException;
import com.kopach.logistics.order.dto.CreateOrderRequest;
import com.kopach.logistics.order.dto.OrderCreatedEvent;
import com.kopach.logistics.order.dto.OrderResponse;
import com.kopach.logistics.order.dto.OrderStatusChangedEvent;
import com.kopach.logistics.order.entity.Order;
import com.kopach.logistics.order.entity.OrderStatus;
import com.kopach.logistics.order.repository.OrderRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

@Service
public class OrderService {

    private final OrderRepository orderRepository;
    private final OrderEventPublisher eventPublisher;
    private final UserRepository userRepository;

    public OrderService(OrderRepository orderRepository,
                        OrderEventPublisher eventPublisher,
                        UserRepository userRepository) {
        this.orderRepository = orderRepository;
        this.eventPublisher = eventPublisher;
        this.userRepository = userRepository;
    }

    public OrderResponse createOrder(CreateOrderRequest request, Long clientId) {
        Order order = new Order();
        order.setClientId(clientId);
        order.setFromAddress(request.getFromAddress());
        order.setToAddress(request.getToAddress());
        order.setPrice(request.getPrice());
        order.setStatus(OrderStatus.NEW);
        order.setCreatedAt(LocalDateTime.now());
        order.setUpdatedAt(LocalDateTime.now());

        Order saved = orderRepository.save(order);

        OrderCreatedEvent event = new OrderCreatedEvent(
                saved.getId(),
                saved.getClientId(),
                saved.getFromAddress(),
                saved.getToAddress(),
                saved.getPrice(),
                saved.getStatus().name(),
                saved.getCreatedAt()
        );
        eventPublisher.publishOrderCreated(event);

        return mapToResponse(saved);
    }

    public List<OrderResponse> getMyOrders(Long clientId) {
        return orderRepository.findByClientId(clientId).stream()
                .map(this::mapToResponse)
                .toList();
    }

    public List<OrderResponse> getAssignedOrders(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("User not found"));

        if (user.getDriverId() == null) {
            return Collections.emptyList();
        }

        return orderRepository.findByDriverId(user.getDriverId()).stream()
                .map(this::mapToResponse)
                .toList();
    }

    // @Transactional обязателен: блокировка строки из findByIdForUpdate
    // держится ровно до конца транзакции (до выхода из метода).
    @Transactional
    public OrderResponse cancelOrder(Long orderId, Long clientId) {
        // Блокируем заказ: если в этот момент DriverAssignedListener назначает водителя,
        // ждём его коммита и проверяем уже свежий статус, а не устаревший NEW.
        Order order = orderRepository.findByIdForUpdate(orderId)
                .orElseThrow(() -> new NotFoundException("Order not found"));

        if (!order.getClientId().equals(clientId)) {
            throw new ForbiddenException("Access denied");
        }
        if (order.getStatus() != OrderStatus.NEW) {
            throw new IllegalArgumentException("Cannot cancel order in status " + order.getStatus());
        }

        OrderStatus oldStatus = order.getStatus();
        order.setStatus(OrderStatus.CANCELLED);
        order.setUpdatedAt(LocalDateTime.now());
        Order updated = orderRepository.save(order);

        // статус изменился — публикуем событие
        eventPublisher.publishOrderStatusChanged(new OrderStatusChangedEvent(
                updated.getId(),
                updated.getDriverId(),
                oldStatus.name(),
                updated.getStatus().name()
        ));

        return mapToResponse(updated);
    }

    @Transactional
    public OrderResponse updateStatus(Long orderId, OrderStatus newStatus, Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("User not found"));

        if (user.getDriverId() == null) {
            throw new ForbiddenException("User is not a driver");
        }

        // Блокируем заказ, чтобы два одновременных запроса не прошли одну и ту же проверку перехода
        Order order = orderRepository.findByIdForUpdate(orderId)
                .orElseThrow(() -> new NotFoundException("Order not found"));

        if (!user.getDriverId().equals(order.getDriverId())) {
            throw new ForbiddenException("Access denied");
        }

        OrderStatus oldStatus = order.getStatus();
        if (!oldStatus.canTransitionTo(newStatus)) {
            throw new IllegalArgumentException(
                    "Cannot transition from " + oldStatus + " to " + newStatus
            );
        }

        order.setStatus(newStatus);
        order.setUpdatedAt(LocalDateTime.now());
        Order updated = orderRepository.save(order);

        eventPublisher.publishOrderStatusChanged(new OrderStatusChangedEvent(
                updated.getId(),
                updated.getDriverId(),
                oldStatus.name(),
                newStatus.name()
        ));

        return mapToResponse(updated);
    }

    private OrderResponse mapToResponse(Order order) {
        return new OrderResponse(
                order.getId(),
                order.getClientId(),
                order.getDriverId(),
                order.getStatus(),
                order.getFromAddress(),
                order.getToAddress(),
                order.getPrice(),
                order.getCreatedAt(),
                order.getUpdatedAt()
        );
    }
}