package com.kopach.logistics.outbox;

import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, Long> {

    // Неотправленные события по порядку. SELECT ... FOR UPDATE SKIP LOCKED (lock.timeout = -2):
    // если запущено несколько экземпляров core, каждый берёт свои строки и не отправляет чужие
    // повторно. Работает только внутри @Transactional
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "-2"))
    @Query("select e from OutboxEvent e where e.sentAt is null order by e.id")
    List<OutboxEvent> findUnsentForUpdate(Pageable batch);

    // Отправленные события храним неделю (для разбора проблем), потом удаляем
    @Modifying
    @Query("delete from OutboxEvent e where e.sentAt < :before")
    int deleteSentBefore(@Param("before") LocalDateTime before);

    long countBySentAtIsNull();
}
