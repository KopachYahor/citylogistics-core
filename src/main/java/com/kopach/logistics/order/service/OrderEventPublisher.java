package com.kopach.logistics.order.service;

import com.kopach.logistics.order.dto.OrderCreatedEvent;
import com.kopach.logistics.order.dto.OrderStatusChangedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Service
public class OrderEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(OrderEventPublisher.class);

    private static final String TOPIC_ORDER_CREATED = "order.created";
    private static final String TOPIC_ORDER_STATUS_CHANGED = "order.status-changed";

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public OrderEventPublisher(KafkaTemplate<String, Object> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public void publishOrderCreated(OrderCreatedEvent event) {
        log.info("Publishing order.created: orderId={}", event.getOrderId());
        kafkaTemplate.send(TOPIC_ORDER_CREATED, event.getOrderId().toString(), event);
    }

    public void publishOrderStatusChanged(OrderStatusChangedEvent event) {
        log.info("Publishing order.status-changed: orderId={}, {} -> {}",
                event.getOrderId(), event.getOldStatus(), event.getNewStatus());
        kafkaTemplate.send(TOPIC_ORDER_STATUS_CHANGED, event.getOrderId().toString(), event);
    }
}