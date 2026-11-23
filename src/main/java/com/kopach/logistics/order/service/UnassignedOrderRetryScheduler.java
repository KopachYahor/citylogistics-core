package com.kopach.logistics.order.service;

import com.kopach.logistics.order.entity.Order;
import com.kopach.logistics.order.entity.OrderStatus;
import com.kopach.logistics.order.repository.OrderRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

// Повтор назначения. Если в момент создания заказа свободных водителей не было или событие
// order.created потерялось (Kafka была недоступна), заказ навсегда остался бы NEW. Раз в
// interval планировщик заново публикует order.created для заказов, которые в NEW дольше
// min-age. Повтор безопасен: driver-service не создаёт второе назначение (идемпотентность),
// а если назначение уже есть — отправляет его ещё раз, и core примет его как обычно
@Component
@ConditionalOnProperty(name = "orders.assignment-retry.enabled", havingValue = "true", matchIfMissing = true)
public class UnassignedOrderRetryScheduler {

    private static final Logger log = LoggerFactory.getLogger(UnassignedOrderRetryScheduler.class);

    private final OrderRepository orderRepository;
    private final OrderEventPublisher eventPublisher;
    private final Duration minAge;

    public UnassignedOrderRetryScheduler(OrderRepository orderRepository,
                                         OrderEventPublisher eventPublisher,
                                         @Value("${orders.assignment-retry.min-age:30s}") Duration minAge) {
        this.orderRepository = orderRepository;
        this.eventPublisher = eventPublisher;
        this.minAge = minAge;
    }

    // fixedDelay: следующий запуск через interval после окончания предыдущего, запуски не накладываются.
    // Значение — миллисекунды или ISO-8601 (PT30S); запись вида 30s @Scheduled не принимает
    @Scheduled(fixedDelayString = "${orders.assignment-retry.interval:PT30S}",
            initialDelayString = "${orders.assignment-retry.interval:PT30S}")
    public void republishUnassignedOrders() {
        // Совсем свежие заказы не трогаем: их первое событие, скорее всего, ещё обрабатывается
        List<Order> waiting = orderRepository.findTop50ByStatusAndCreatedAtBeforeOrderByCreatedAtAsc(
                OrderStatus.NEW, LocalDateTime.now().minus(minAge));
        if (waiting.isEmpty()) {
            return;
        }
        log.info("Re-publishing order.created for {} order(s) still without a driver", waiting.size());
        // Самые старые первыми: при одном освободившемся водителе он достанется тому, кто ждёт дольше
        waiting.forEach(order -> eventPublisher.publishOrderCreated(OrderService.orderCreatedEvent(order)));
    }
}
