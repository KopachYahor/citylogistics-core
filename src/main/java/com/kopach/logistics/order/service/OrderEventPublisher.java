package com.kopach.logistics.order.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kopach.logistics.order.dto.OrderCreatedEvent;
import com.kopach.logistics.order.dto.OrderStatusChangedEvent;
import com.kopach.logistics.outbox.OutboxEvent;
import com.kopach.logistics.outbox.OutboxEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

// Transactional Outbox: событие не отправляется в Kafka напрямую, а записывается в outbox_events
// в той же транзакции, что и изменение заказа. Либо сохранится и заказ, и событие, либо ничего.
// В Kafka его отправит OutboxRelay. Ключ сообщения — id заказа: события одного заказа
// попадают в одну партицию и идут по порядку
@Service
public class OrderEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(OrderEventPublisher.class);

    static final String TOPIC_ORDER_CREATED = "order.created";
    static final String TOPIC_ORDER_STATUS_CHANGED = "order.status-changed";

    private final OutboxEventRepository outboxRepository;
    private final ObjectMapper objectMapper;

    public OrderEventPublisher(OutboxEventRepository outboxRepository, ObjectMapper objectMapper) {
        this.outboxRepository = outboxRepository;
        this.objectMapper = objectMapper;
    }

    // MANDATORY: вызов без открытой транзакции — ошибка. Иначе событие могло бы записаться
    // отдельно от заказа, и вернулась бы та же проблема двойной записи
    @Transactional(propagation = Propagation.MANDATORY)
    public void publishOrderCreated(OrderCreatedEvent event) {
        log.info("Saving order.created to outbox: orderId={}", event.getOrderId());
        save(TOPIC_ORDER_CREATED, event.getOrderId(), event);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void publishOrderStatusChanged(OrderStatusChangedEvent event) {
        log.info("Saving order.status-changed to outbox: orderId={}, {} -> {}",
                event.getOrderId(), event.getOldStatus(), event.getNewStatus());
        save(TOPIC_ORDER_STATUS_CHANGED, event.getOrderId(), event);
    }

    private void save(String topic, Long orderId, Object event) {
        String payload;
        try {
            payload = objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException e) {
            // Событие — наш собственный DTO, поэтому это ошибка в коде: откатываем всю транзакцию
            throw new IllegalStateException("Cannot serialize " + event.getClass().getSimpleName(), e);
        }
        outboxRepository.save(new OutboxEvent(topic, orderId.toString(), event.getClass().getName(), payload));
    }
}
