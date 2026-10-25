package com.capstone.steam.cart;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface CartItemRepository extends JpaRepository<CartItem, Long> {

    @Query("select c from CartItem c join fetch c.game where c.user.id = :userId order by c.addedAt")
    List<CartItem> findByUserIdWithGame(Long userId);

    boolean existsByUserIdAndGameId(Long userId, Long gameId);

    @Modifying
    @Query("delete from CartItem c where c.user.id = :userId and c.game.id = :gameId")
    int deleteByUserIdAndGameId(Long userId, Long gameId);

    /** Dopo il pagamento si tolgono solo i giochi acquistati (l'utente può averne aggiunti altri dopo aver creato l'ordine). */
    @Modifying
    @Query("delete from CartItem c where c.user.id = :userId and c.game.id in :gameIds")
    int deleteByUserIdAndGameIdIn(Long userId, Collection<Long> gameIds);

    @Modifying
    @Query("delete from CartItem c where c.user.id = :userId")
    int deleteAllByUserId(Long userId);
}
