package com.kopach.logistics.order.repository;

import com.kopach.logistics.order.entity.Order;
import com.kopach.logistics.order.entity.OrderStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface OrderRepository extends JpaRepository<Order, Long> {

    List<Order> findByClientId(Long clientId);

    List<Order> findByDriverId(Long driverId);

    // Самые старые заказы, которые до сих пор без водителя (для повтора назначения)
    List<Order> findTop50ByStatusAndCreatedAtBeforeOrderByCreatedAtAsc(OrderStatus status, LocalDateTime createdBefore);

    // SELECT ... FOR UPDATE: вторая транзакция ждёт коммита первой.
    // Работает только внутри @Transactional.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from Order o where o.id = :id")
    Optional<Order> findByIdForUpdate(@Param("id") Long id);
}
