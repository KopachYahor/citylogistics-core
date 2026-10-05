package com.kopach.logistics.order.service;

import com.kopach.logistics.order.dto.CreateOrderRequest;
import com.kopach.logistics.order.dto.OrderResponse;
import com.kopach.logistics.order.entity.Order;
import com.kopach.logistics.order.entity.OrderStatus;
import com.kopach.logistics.order.repository.OrderRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class OrderService {

    private final OrderRepository orderRepository;
    private final OrderEventPublisher eventPublisher;

    public OrderService(OrderRepository orderRepository, OrderEventPublisher eventPublisher) {
        this.orderRepository = orderRepository;
        this.eventPublisher = eventPublisher;
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
        eventPublisher.publishOrderCreated(saved.getId());
        return mapToResponse(saved);
    }

    public List<OrderResponse> getMyOrders(Long clientId) {
        return orderRepository.findByClientId(clientId).stream()
                .map(this::mapToResponse)
                .toList();
    }

    public OrderResponse cancelOrder(Long orderId, Long clientId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new IllegalArgumentException("Order not found"));

        if (!order.getClientId().equals(clientId)) {
            throw new IllegalArgumentException("Access denied");
        }
        if (order.getStatus() != OrderStatus.NEW) {
            throw new IllegalArgumentException("Cannot cancel order in status " + order.getStatus());
        }

        order.setStatus(OrderStatus.CANCELLED);
        order.setUpdatedAt(LocalDateTime.now());
        Order updated = orderRepository.save(order);
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