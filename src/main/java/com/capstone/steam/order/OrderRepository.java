package com.capstone.steam.order;

import java.time.Instant;
import java.util.Optional;

import jakarta.persistence.LockModeType;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface OrderRepository extends JpaRepository<Order, Long> {

    Page<Order> findByUserId(Long userId, Pageable pageable);

    @EntityGraph(attributePaths = {"items", "items.game"})
    Optional<Order> findWithItemsById(Long id);

    /**
     * Legge l'ordine proprio bloccando la riga (D33): un secondo pagamento dello stesso ordine aspetta il
     * commit del primo e vede già lo stato finale, così non arriva a chiamare il gateway.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from Order o where o.id = :orderId and o.user.id = :userId")
    Optional<Order> findOwnForUpdate(Long orderId, Long userId);

    // Transizioni di stato atomiche: l'"and o.status = PENDING" fa sì che
    // una sola transazione possa far uscire l'ordine da PENDING. Se restituisce 0,
    // l'ordine era già pagato, fallito o annullato (es. un doppio clic).

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update Order o
               set o.status = com.capstone.steam.order.OrderStatus.PAID,
                   o.paidAt = :paidAt
             where o.id = :orderId
               and o.status = com.capstone.steam.order.OrderStatus.PENDING""")
    int markPaid(Long orderId, Instant paidAt);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update Order o
               set o.status = :newStatus
             where o.id = :orderId
               and o.status = com.capstone.steam.order.OrderStatus.PENDING""")
    int closePending(Long orderId, OrderStatus newStatus);
}
