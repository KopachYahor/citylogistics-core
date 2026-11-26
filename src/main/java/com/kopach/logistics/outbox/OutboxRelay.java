package com.kopach.logistics.outbox;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.TimeUnit;

// Вторая половина Transactional Outbox: забирает из outbox_events неотправленные события
// и отправляет их в Kafka. Доставка «хотя бы один раз»: если Kafka приняла событие, а отметку
// sent_at сохранить не удалось, событие уйдёт повторно. Потребители к этому готовы (идемпотентны)
@Component
public class OutboxRelay {

    private static final Logger log = LoggerFactory.getLogger(OutboxRelay.class);

    private final OutboxEventRepository outboxRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final int batchSize;
    private final Duration sendTimeout;
    private final Duration retention;

    public OutboxRelay(OutboxEventRepository outboxRepository,
                       KafkaTemplate<String, Object> kafkaTemplate,
                       ObjectMapper objectMapper,
                       @Value("${outbox.relay.batch-size:100}") int batchSize,
                       @Value("${outbox.relay.send-timeout:10s}") Duration sendTimeout,
                       @Value("${outbox.retention:7d}") Duration retention) {
        this.outboxRepository = outboxRepository;
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
        this.batchSize = batchSize;
        this.sendTimeout = sendTimeout;
        this.retention = retention;
    }

    // Интервалы в ISO-8601 (PT1S): запись вида 1s @Scheduled не принимает
    @Scheduled(fixedDelayString = "${outbox.relay.interval:PT1S}",
            initialDelayString = "${outbox.relay.initial-delay:PT5S}")
    @Transactional
    public void publishPending() {
        List<OutboxEvent> batch = outboxRepository.findUnsentForUpdate(PageRequest.of(0, batchSize));
        for (OutboxEvent outboxEvent : batch) {
            Object event;
            try {
                // Восстанавливаем объект события: JsonSerializer положит имя его класса в __TypeId__,
                // поэтому получатели видят то же сообщение, что и до Outbox
                event = objectMapper.readValue(outboxEvent.getPayload(), Class.forName(outboxEvent.getEventType()));
            } catch (ClassNotFoundException | JsonProcessingException e) {
                // Такое событие не отправить никогда (ошибка в коде). Пропускаем, чтобы оно
                // не держало очередь вечно, и оставляем след в логе
                log.error("Skipping outbox event {}: cannot read {}: {}",
                        outboxEvent.getId(), outboxEvent.getEventType(), e.toString());
                outboxEvent.markSent();
                continue;
            }

            try {
                // Ждём подтверждения Kafka: отметку «отправлено» ставим только после него
                kafkaTemplate.send(outboxEvent.getTopic(), outboxEvent.getMessageKey(), event)
                        .get(sendTimeout.toMillis(), TimeUnit.MILLISECONDS);
                outboxEvent.markSent();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            } catch (Exception e) {
                // Kafka недоступна. Останавливаемся, а не идём дальше: события одного заказа должны
                // уйти по порядку (DELIVERED не раньше IN_TRANSIT). Повторим при следующем запуске
                log.warn("Kafka is unavailable, {} outbox event(s) wait for the next attempt: {}",
                        outboxRepository.countBySentAtIsNull(), e.toString());
                return;
            }
        }
    }

    // Раз в час удаляем отправленные события старше outbox.retention, чтобы таблица не росла
    @Scheduled(cron = "${outbox.cleanup-cron:0 0 * * * *}")
    @Transactional
    public void deleteOldSentEvents() {
        int deleted = outboxRepository.deleteSentBefore(LocalDateTime.now().minus(retention));
        if (deleted > 0) {
            log.info("Deleted {} sent outbox event(s) older than {}", deleted, retention);
        }
    }
}
