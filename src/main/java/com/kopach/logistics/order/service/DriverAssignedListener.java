package com.kopach.logistics.order.service;

import com.kopach.logistics.driver.dto.DriverAssignedEvent;
import com.kopach.logistics.order.dto.OrderStatusChangedEvent;
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
    private final OrderEventPublisher eventPublisher;

    public DriverAssignedListener(OrderRepository orderRepository, OrderEventPublisher eventPublisher) {
        this.orderRepository = orderRepository;
        this.eventPublisher = eventPublisher;
    }

    @KafkaListener(topics = "driver.assigned", groupId = "logistics-group")
    @Transactional
    public void onDriverAssigned(DriverAssignedEvent event) {
        log.info("Received driver.assigned: orderId={}, driverId={}",
                event.getOrderId(), event.getDriverId());

        // Отмена заказа клиентом в этот момент подождёт коммита
        Order order = orderRepository.findByIdForUpdate(event.getOrderId())
                .orElseThrow(() -> new IllegalStateException(
                        "Order not found: " + event.getOrderId()));

        // Событие асинхронное: заказ мог уже измениться
        if (!order.getStatus().canTransitionTo(OrderStatus.ACCEPTED)) {
            if (order.getStatus() == OrderStatus.CANCELLED) {
                // В событии отмены driverId был null: повторяем его с водителем, иначе тот останется BUSY
                log.warn("Order {} was cancelled before driver {} was assigned, releasing driver",
                        order.getId(), event.getDriverId());
                eventPublisher.publishOrderStatusChanged(new OrderStatusChangedEvent(
                        order.getId(),
                        event.getDriverId(),
                        OrderStatus.CANCELLED.name(),
                        OrderStatus.CANCELLED.name()
                ));
            } else {
                // например, повторная доставка (at-least-once)
                log.info("Order {} is already {}, ignoring driver.assigned", order.getId(), order.getStatus());
            }
            return;
        }

        order.setDriverId(event.getDriverId());
        order.setStatus(OrderStatus.ACCEPTED);
        order.setUpdatedAt(LocalDateTime.now());
        orderRepository.save(order);

        log.info("Order {} assigned to driver {}, status=ACCEPTED",
                order.getId(), event.getDriverId());
    }
}
