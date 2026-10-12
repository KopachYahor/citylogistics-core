package com.kopach.logistics.order.service;

import com.kopach.logistics.order.dto.OrderCreatedEvent;
import com.kopach.logistics.order.entity.Order;
import com.kopach.logistics.order.entity.OrderStatus;
import com.kopach.logistics.order.repository.OrderRepository;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
public class OrderProcessingListener {

    private final OrderRepository orderRepository;

    public OrderProcessingListener(OrderRepository orderRepository) {
        this.orderRepository = orderRepository;
    }

    @KafkaListener(topics = "order.created", groupId = "logistics-group")
    public void handleOrderCreated(OrderCreatedEvent event) {
        Order order = orderRepository.findById(event.getOrderId())
                .orElseThrow(() -> new IllegalStateException("Order not found: " + event.getOrderId()));

        order.setStatus(OrderStatus.PROCESSING);
        order.setUpdatedAt(LocalDateTime.now());
        orderRepository.save(order);
    }
}