package com.kopach.logistics.order.repository;

import com.kopach.logistics.order.entity.Order;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface OrderRepository extends JpaRepository<Order, Long> {

    List<Order> findByClientId(Long clientId);

    List<Order> findByDriverId(Long driverId);

    /**
     * Читает заказ и блокирует его строку до конца транзакции (SELECT ... FOR UPDATE).
     * Если строку уже держит другая транзакция, этот вызов ждёт её коммита
     * и потом читает уже новые данные. Так две операции не затрут друг друга.
     * Работает только внутри @Transactional: без транзакции блокировка сразу снимается.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from Order o where o.id = :id")
    Optional<Order> findByIdForUpdate(@Param("id") Long id);
}
