package com.kopach.logistics.order.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.kopach.logistics.order.dto.OrderCreatedEvent;
import com.kopach.logistics.order.dto.OrderStatusChangedEvent;
import com.kopach.logistics.outbox.OutboxEvent;
import com.kopach.logistics.outbox.OutboxEventRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class OrderEventPublisherTest {

    private final OutboxEventRepository outboxRepository = mock(OutboxEventRepository.class);
    // Настроен как ObjectMapper из Spring Boot: LocalDateTime поддерживается и пишется строкой
    private final OrderEventPublisher publisher = new OrderEventPublisher(outboxRepository,
            new ObjectMapper().findAndRegisterModules().disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS));

    @Test
    void statusChanged_shouldBeSavedToOutboxNotSentToKafka() {
        publisher.publishOrderStatusChanged(new OrderStatusChangedEvent(1L, 5L, "ARRIVED", "DELIVERED"));

        OutboxEvent saved = savedEvent();
        assertThat(saved.getTopic()).isEqualTo("order.status-changed");
        assertThat(saved.getMessageKey()).isEqualTo("1"); // ключ — id заказа
        assertThat(saved.getEventType()).isEqualTo(OrderStatusChangedEvent.class.getName());
        assertThat(saved.getPayload()).contains("\"newStatus\":\"DELIVERED\"").contains("\"driverId\":5");
        assertThat(saved.getSentAt()).isNull(); // отправит OutboxRelay
    }

    @Test
    void orderCreated_shouldKeepDatesReadable() {
        publisher.publishOrderCreated(new OrderCreatedEvent(7L, 1L, "Minsk", "Grodno",
                BigDecimal.valueOf(300), "NEW", LocalDateTime.of(2026, 11, 26, 12, 0)));

        OutboxEvent saved = savedEvent();
        assertThat(saved.getTopic()).isEqualTo("order.created");
        assertThat(saved.getMessageKey()).isEqualTo("7");
        assertThat(saved.getPayload()).contains("\"createdAt\":\"2026-11-26T12:00:00\"");
    }

    private OutboxEvent savedEvent() {
        ArgumentCaptor<OutboxEvent> captor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outboxRepository).save(captor.capture());
        return captor.getValue();
    }
}
