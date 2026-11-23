package com.kopach.logistics.order.service;

import com.kopach.logistics.order.dto.OrderStatusChangedEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OrderEventPublisherTest {

    @SuppressWarnings("unchecked")
    private final KafkaTemplate<String, Object> kafkaTemplate = mock(KafkaTemplate.class);
    private final OrderEventPublisher publisher = new OrderEventPublisher(kafkaTemplate);

    @AfterEach
    void clearTransaction() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void insideTransaction_shouldSendOnlyAfterCommit() {
        when(kafkaTemplate.send(anyString(), anyString(), any())).thenReturn(new CompletableFuture<>());
        TransactionSynchronizationManager.initSynchronization(); // как внутри @Transactional

        publisher.publishOrderStatusChanged(new OrderStatusChangedEvent(1L, 5L, "ARRIVED", "DELIVERED"));
        // до коммита в Kafka ничего не ушло: при откате никто не узнает о несуществующем изменении
        verify(kafkaTemplate, never()).send(anyString(), anyString(), any());

        TransactionSynchronizationManager.getSynchronizations().forEach(TransactionSynchronization::afterCommit);
        verify(kafkaTemplate).send(eq("order.status-changed"), eq("1"), any(OrderStatusChangedEvent.class));
    }

    @Test
    void outsideTransaction_shouldSendImmediately() {
        when(kafkaTemplate.send(anyString(), anyString(), any())).thenReturn(new CompletableFuture<>());

        publisher.publishOrderStatusChanged(new OrderStatusChangedEvent(1L, 5L, "ARRIVED", "DELIVERED"));

        verify(kafkaTemplate).send(eq("order.status-changed"), eq("1"), any(OrderStatusChangedEvent.class));
    }

    @Test
    void kafkaFailure_shouldBeLoggedNotThrown() {
        // данные уже сохранены: 500 клиенту сказал бы «не получилось», что неправда
        when(kafkaTemplate.send(anyString(), anyString(), any())).thenThrow(new RuntimeException("broker is down"));

        assertThatCode(() -> publisher.publishOrderStatusChanged(
                new OrderStatusChangedEvent(1L, 5L, "ARRIVED", "DELIVERED"))).doesNotThrowAnyException();
    }
}
