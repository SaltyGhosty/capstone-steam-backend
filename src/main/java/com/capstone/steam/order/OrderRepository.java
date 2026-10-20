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
     * Lee el pedido propio bloqueando la fila (D33): un segundo pago del mismo pedido espera al
     * commit del primero y ve ya el estado final, así no llega a llamar a la pasarela.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from Order o where o.id = :orderId and o.user.id = :userId")
    Optional<Order> findOwnForUpdate(Long orderId, Long userId);

    // ------------------------------------------------------------------
    // Transiciones de estado atómicas: el "and o.status = PENDING" hace que
    // solo una transacción pueda sacar el pedido de PENDING. Si devuelve 0,
    // el pedido ya estaba pagado, fallido o cancelado (p. ej. un doble clic).
    // ------------------------------------------------------------------

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
