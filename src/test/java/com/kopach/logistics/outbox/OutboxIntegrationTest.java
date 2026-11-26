package com.kopach.logistics.outbox;

import com.kopach.logistics.auth.entity.Role;
import com.kopach.logistics.auth.entity.User;
import com.kopach.logistics.auth.repository.UserRepository;
import com.kopach.logistics.config.AbstractPostgresTest;
import com.kopach.logistics.order.dto.CreateOrderRequest;
import com.kopach.logistics.order.dto.OrderCreatedEvent;
import com.kopach.logistics.order.dto.OrderStatusChangedEvent;
import com.kopach.logistics.order.repository.OrderRepository;
import com.kopach.logistics.order.service.OrderEventPublisher;
import com.kopach.logistics.order.service.OrderService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// Outbox на настоящей PostgreSQL (Testcontainers): атомарность заказа и события,
// отправка через OutboxRelay (запрос с FOR UPDATE SKIP LOCKED) и защита от вызова без транзакции
@SpringBootTest
class OutboxIntegrationTest extends AbstractPostgresTest {

    @Autowired
    private OrderService orderService;
    @Autowired
    private OrderEventPublisher eventPublisher;
    @Autowired
    private OutboxRelay relay;
    @Autowired
    private OutboxEventRepository outboxRepository;
    @Autowired
    private OrderRepository orderRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private TransactionTemplate transactionTemplate;

    // Настоящая Kafka не нужна: проверяем, что relay отдаёт ей событие
    @MockBean
    private KafkaTemplate<String, Object> kafkaTemplate;

    private Long clientId;

    @BeforeEach
    void setUp() {
        outboxRepository.deleteAll();
        orderRepository.deleteAll();
        userRepository.deleteAll();
        clientId = userRepository.save(new User("outbox@test.local", "kc-outbox", Role.CLIENT, LocalDateTime.now())).getId();
    }

    @Test
    void createOrder_shouldSaveOrderAndEventTogether() {
        Long orderId = orderService.createOrder(request(), clientId).getId();

        assertThat(orderRepository.findById(orderId)).isPresent();
        assertThat(outboxRepository.findAll())
                .singleElement()
                .satisfies(e -> {
                    assertThat(e.getTopic()).isEqualTo("order.created");
                    assertThat(e.getMessageKey()).isEqualTo(orderId.toString());
                    assertThat(e.getSentAt()).isNull();
                });
    }

    @Test
    void rollback_shouldRemoveBothOrderAndEvent() {
        // Ошибка после записи заказа и события: откатываются оба, «события о несуществующем» нет
        assertThatThrownBy(() -> transactionTemplate.executeWithoutResult(status -> {
            orderService.createOrder(request(), clientId);
            throw new IllegalStateException("something failed after saving");
        })).isInstanceOf(IllegalStateException.class);

        assertThat(orderRepository.count()).isZero();
        assertThat(outboxRepository.count()).isZero();
    }

    @Test
    void relay_shouldSendPendingEventAndMarkItSent() {
        when(kafkaTemplate.send(anyString(), anyString(), any())).thenReturn(CompletableFuture.completedFuture(null));
        Long orderId = orderService.createOrder(request(), clientId).getId();

        relay.publishPending();

        verify(kafkaTemplate).send(eq("order.created"), eq(orderId.toString()), any(OrderCreatedEvent.class));
        assertThat(outboxRepository.countBySentAtIsNull()).isZero();
    }

    @Test
    void relay_whenKafkaIsDown_shouldKeepEventForLater() {
        when(kafkaTemplate.send(anyString(), anyString(), any()))
                .thenReturn(CompletableFuture.failedFuture(new RuntimeException("broker is down")));
        orderService.createOrder(request(), clientId);

        relay.publishPending();

        assertThat(outboxRepository.countBySentAtIsNull()).isEqualTo(1);
    }

    @Test
    void publisherWithoutTransaction_shouldFail() {
        // Propagation.MANDATORY: событие нельзя записать отдельно от изменения заказа
        assertThatThrownBy(() -> eventPublisher.publishOrderStatusChanged(
                new OrderStatusChangedEvent(1L, 5L, "ARRIVED", "DELIVERED")))
                .isInstanceOf(IllegalTransactionStateException.class);
    }

    private CreateOrderRequest request() {
        return new CreateOrderRequest("Minsk", "Grodno", BigDecimal.valueOf(300));
    }
}
