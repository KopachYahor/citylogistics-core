package com.kopach.logistics.order.service;

import com.kopach.logistics.order.dto.OrderCreatedEvent;
import com.kopach.logistics.order.dto.OrderStatusChangedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

// Ключ сообщения — id заказа: события одного заказа попадают в одну партицию и идут по порядку
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
        sendAfterCommit(TOPIC_ORDER_CREATED, event.getOrderId().toString(), event);
    }

    public void publishOrderStatusChanged(OrderStatusChangedEvent event) {
        log.info("Publishing order.status-changed: orderId={}, {} -> {}",
                event.getOrderId(), event.getOldStatus(), event.getNewStatus());
        sendAfterCommit(TOPIC_ORDER_STATUS_CHANGED, event.getOrderId().toString(), event);
    }

    // Внутри транзакции отправляем только после успешного коммита: если коммит упадёт,
    // другой сервис не узнает об изменении, которого нет в базе. Вне транзакции (запись уже
    // сохранена) — сразу. Это не Outbox: если Kafka недоступна после коммита, событие теряется
    // (для order.created его повторит UnassignedOrderRetryScheduler)
    private void sendAfterCommit(String topic, String key, Object event) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    send(topic, key, event);
                }
            });
        } else {
            send(topic, key, event);
        }
    }

    // Ошибку отправки пишем в лог, а не бросаем: данные уже сохранены, а 500 клиенту
    // означал бы «заказ не создан», что неправда
    private void send(String topic, String key, Object event) {
        try {
            kafkaTemplate.send(topic, key, event).whenComplete((result, ex) -> {
                if (ex != null) {
                    log.error("Kafka did not accept {} for order {}: {}", topic, key, ex.toString());
                }
            });
        } catch (RuntimeException e) {
            // send() бросает сразу, если брокер недоступен дольше max.block.ms (5 с)
            log.error("Could not send {} for order {}: {}", topic, key, e.toString());
        }
    }
}
