package com.kopach.logistics.order.service;

import com.kopach.logistics.driver.dto.DriverAssignedEvent;
import com.kopach.logistics.order.entity.Order;
import com.kopach.logistics.order.entity.OrderStatus;
import com.kopach.logistics.order.repository.OrderRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Component
public class DriverAssignedListener {

    private static final Logger log = LoggerFactory.getLogger(DriverAssignedListener.class);

    private final OrderRepository orderRepository;

    public DriverAssignedListener(OrderRepository orderRepository) {
        this.orderRepository = orderRepository;
    }

    @KafkaListener(topics = "driver.assigned", groupId = "logistics-group")
    @Transactional
    public void onDriverAssigned(DriverAssignedEvent event) {
        log.info("Received driver.assigned: orderId={}, driverId={}",
                event.getOrderId(), event.getDriverId());

        Order order = orderRepository.findById(event.getOrderId())
                .orElseThrow(() -> new IllegalStateException(
                        "Order not found: " + event.getOrderId()));

        order.setDriverId(event.getDriverId());
        order.setStatus(OrderStatus.PROCESSING);
        order.setUpdatedAt(LocalDateTime.now());
        orderRepository.save(order);

        log.info("Order {} assigned to driver {}, status=PROCESSING",
                order.getId(), event.getDriverId());
    }
}