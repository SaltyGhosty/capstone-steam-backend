package com.capstone.steam.wishlist;

import java.util.Collection;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import com.capstone.steam.notification.event.SaleRecipient;

public interface WishlistItemRepository extends JpaRepository<WishlistItem, Long> {

    /** Chi avvisare quando un gioco va in offerta: chi lo ha nella wishlist e resta attivo. */
    @Query("""
            select new com.capstone.steam.notification.event.SaleRecipient(u.email, u.displayName, u.locale)
            from WishlistItem w join w.user u
            where w.game.id = :gameId and u.enabled = true""")
    List<SaleRecipient> findSaleRecipients(Long gameId);

    @Query(value = "select w from WishlistItem w join fetch w.game where w.user.id = :userId",
            countQuery = "select count(w) from WishlistItem w where w.user.id = :userId")
    Page<WishlistItem> findByUserId(Long userId, Pageable pageable);

    boolean existsByUserIdAndGameId(Long userId, Long gameId);

    @Modifying
    @Query("delete from WishlistItem w where w.user.id = :userId and w.game.id = :gameId")
    int deleteByUserIdAndGameId(Long userId, Long gameId);

    /** All'anonimizzazione dell'account: si svuota l'intera wishlist. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from WishlistItem w where w.user.id = :userId")
    int deleteAllByUserId(Long userId);

    /** Dopo un acquisto, i giochi acquistati escono dalla wishlist. */
    @Modifying
    @Query("delete from WishlistItem w where w.user.id = :userId and w.game.id in :gameIds")
    int deleteByUserIdAndGameIdIn(Long userId, Collection<Long> gameIds);
}
