package com.kopach.logistics.outbox;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kopach.logistics.order.dto.OrderStatusChangedEvent;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Pageable;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OutboxRelayTest {

    private final OutboxEventRepository outboxRepository = mock(OutboxEventRepository.class);
    @SuppressWarnings("unchecked")
    private final KafkaTemplate<String, Object> kafkaTemplate = mock(KafkaTemplate.class);
    private final OutboxRelay relay = new OutboxRelay(outboxRepository, kafkaTemplate,
            new ObjectMapper().findAndRegisterModules(), 100, Duration.ofSeconds(1), Duration.ofDays(7));

    @Test
    void shouldSendInOrderAndMarkAsSent() {
        OutboxEvent first = statusEvent(1L, "IN_TRANSIT");
        OutboxEvent second = statusEvent(1L, "ARRIVED");
        when(outboxRepository.findUnsentForUpdate(any(Pageable.class))).thenReturn(List.of(first, second));
        when(kafkaTemplate.send(anyString(), anyString(), any())).thenReturn(accepted());

        relay.publishPending();

        ArgumentCaptor<Object> sent = ArgumentCaptor.forClass(Object.class);
        verify(kafkaTemplate, times(2)).send(eq("order.status-changed"), eq("1"), sent.capture());
        // порядок как в outbox: сначала IN_TRANSIT, потом ARRIVED
        assertThat(sent.getAllValues()).extracting(e -> ((OrderStatusChangedEvent) e).getNewStatus())
                .containsExactly("IN_TRANSIT", "ARRIVED");
        assertThat(first.getSentAt()).isNotNull();
        assertThat(second.getSentAt()).isNotNull();
    }

    @Test
    void kafkaDown_shouldStopAndKeepEventsForNextAttempt() {
        OutboxEvent first = statusEvent(1L, "IN_TRANSIT");
        OutboxEvent second = statusEvent(1L, "ARRIVED");
        when(outboxRepository.findUnsentForUpdate(any(Pageable.class))).thenReturn(List.of(first, second));
        when(kafkaTemplate.send(anyString(), anyString(), any()))
                .thenReturn(CompletableFuture.failedFuture(new RuntimeException("broker is down")));

        relay.publishPending();

        // Первое не принято — второе даже не пробуем, чтобы не нарушить порядок событий заказа
        verify(kafkaTemplate).send(anyString(), anyString(), any());
        assertThat(first.getSentAt()).isNull();
        assertThat(second.getSentAt()).isNull();
    }

    @Test
    void unreadableEvent_shouldBeSkippedNotBlockTheQueue() {
        OutboxEvent broken = new OutboxEvent("order.status-changed", "1", "com.example.Missing", "{}");
        OutboxEvent next = statusEvent(2L, "ARRIVED");
        when(outboxRepository.findUnsentForUpdate(any(Pageable.class))).thenReturn(List.of(broken, next));
        when(kafkaTemplate.send(anyString(), anyString(), any())).thenReturn(accepted());

        relay.publishPending();

        assertThat(broken.getSentAt()).isNotNull(); // помечено, чтобы не блокировать очередь
        verify(kafkaTemplate, never()).send(anyString(), eq("1"), any());
        verify(kafkaTemplate).send(eq("order.status-changed"), eq("2"), any(OrderStatusChangedEvent.class));
        assertThat(next.getSentAt()).isNotNull();
    }

    private OutboxEvent statusEvent(Long orderId, String newStatus) {
        String payload = "{\"orderId\":" + orderId + ",\"driverId\":5,\"oldStatus\":\"ACCEPTED\",\"newStatus\":\"" + newStatus + "\"}";
        return new OutboxEvent("order.status-changed", orderId.toString(), OrderStatusChangedEvent.class.getName(), payload);
    }

    private CompletableFuture<SendResult<String, Object>> accepted() {
        return CompletableFuture.completedFuture(null);
    }
}
