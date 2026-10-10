package com.kopach.logistics.order.repository;

import com.kopach.logistics.auth.entity.Role;
import com.kopach.logistics.auth.entity.User;
import com.kopach.logistics.auth.repository.UserRepository;
import com.kopach.logistics.config.PostgresTestContainer;
import com.kopach.logistics.order.entity.Order;
import com.kopach.logistics.order.entity.OrderStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(PostgresTestContainer.class)
class OrderRepositoryTest {

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private UserRepository userRepository;

    private Long client1Id;
    private Long client2Id;

    @BeforeEach
    void setUp() {
        client1Id = userRepository.save(newUser("client1@test.local")).getId();
        client2Id = userRepository.save(newUser("client2@test.local")).getId();
    }

    @Test
    void saveAndFindByClientId_shouldReturnOrdersForSpecificClient() {
        // given
        Order order1 = createOrder(client1Id, OrderStatus.NEW, "Минск", "Гомель");
        Order order2 = createOrder(client1Id, OrderStatus.PROCESSING, "Минск", "Брест");
        Order order3 = createOrder(client2Id, OrderStatus.NEW, "Минск", "Витебск");

        orderRepository.save(order1);
        orderRepository.save(order2);
        orderRepository.save(order3);

        // when
        List<Order> client1Orders = orderRepository.findByClientId(client1Id);
        List<Order> client2Orders = orderRepository.findByClientId(client2Id);

        // then
        assertThat(client1Orders).hasSize(2);
        assertThat(client2Orders).hasSize(1);
        assertThat(client1Orders).extracting(Order::getStatus)
                .containsExactlyInAnyOrder(OrderStatus.NEW, OrderStatus.PROCESSING);
    }

    @Test
    void findById_shouldReturnSavedOrder() {
        // given
        Order order = createOrder(client1Id, OrderStatus.NEW, "Минск", "Гомель");
        Order saved = orderRepository.save(order);

        // when
        Order found = orderRepository.findById(saved.getId()).orElseThrow();

        // then
        assertThat(found.getId()).isEqualTo(saved.getId());
        assertThat(found.getClientId()).isEqualTo(client1Id);
        assertThat(found.getStatus()).isEqualTo(OrderStatus.NEW);
        assertThat(found.getFromAddress()).isEqualTo("Минск");
        assertThat(found.getPrice()).isEqualByComparingTo(BigDecimal.valueOf(300));
    }

    private User newUser(String email) {
        return new User(
                email,
                "$2a$10$testtesttesttesttesttesttesttesttesttesttesttesttesttest", // фиктивный bcrypt-хэш
                Role.CLIENT,
                LocalDateTime.now()
        );
    }

    private Order createOrder(Long clientId, OrderStatus status, String from, String to) {
        Order order = new Order();
        order.setClientId(clientId);
        order.setStatus(status);
        order.setFromAddress(from);
        order.setToAddress(to);
        order.setPrice(BigDecimal.valueOf(300));
        order.setCreatedAt(LocalDateTime.now());
        order.setUpdatedAt(LocalDateTime.now());
        return order;
    }
}