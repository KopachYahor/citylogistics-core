package com.kopach.logistics.order.service;

import com.kopach.logistics.config.AbstractPostgresTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

// В остальных тестах планировщик выключен (нет Kafka). Здесь он включён, чтобы проверить, что
// приложение стартует с интервалом из настоящего application.yml: его формат @Scheduled проверяет
// только при запуске (запись вида 30s роняла старт). min-age — сутки, поэтому, даже если
// планировщик сработает во время тестов, он ничего не отправит
@SpringBootTest(properties = {
        "orders.assignment-retry.enabled=true",
        "orders.assignment-retry.min-age=P1D"
})
class UnassignedOrderRetrySchedulerContextTest extends AbstractPostgresTest {

    @Autowired
    private UnassignedOrderRetryScheduler scheduler;

    @Test
    void applicationStartsWithSchedulerEnabled() {
        assertThat(scheduler).isNotNull();
    }
}
