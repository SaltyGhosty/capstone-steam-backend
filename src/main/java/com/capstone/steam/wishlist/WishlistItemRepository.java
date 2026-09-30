package com.capstone.steam.wishlist;

import java.util.Collection;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface WishlistItemRepository extends JpaRepository<WishlistItem, Long> {

    @Query(value = "select w from WishlistItem w join fetch w.game where w.user.id = :userId",
            countQuery = "select count(w) from WishlistItem w where w.user.id = :userId")
    Page<WishlistItem> findByUserId(Long userId, Pageable pageable);

    boolean existsByUserIdAndGameId(Long userId, Long gameId);

    @Modifying
    @Query("delete from WishlistItem w where w.user.id = :userId and w.game.id = :gameId")
    int deleteByUserIdAndGameId(Long userId, Long gameId);

    /** Tras una compra, los juegos comprados salen de la wishlist. */
    @Modifying
    @Query("delete from WishlistItem w where w.user.id = :userId and w.game.id in :gameIds")
    int deleteByUserIdAndGameIdIn(Long userId, Collection<Long> gameIds);
}
