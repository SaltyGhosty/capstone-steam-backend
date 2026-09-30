package com.capstone.steam.library;

import java.util.Collection;
import java.util.Set;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface LibraryRepository extends JpaRepository<LibraryEntry, Long> {

    @Query(value = "select l from LibraryEntry l join fetch l.game where l.user.id = :userId",
            countQuery = "select count(l) from LibraryEntry l where l.user.id = :userId")
    Page<LibraryEntry> findByUserId(Long userId, Pageable pageable);

    boolean existsByUserIdAndGameId(Long userId, Long gameId);

    /** De entre gameIds, cuáles ya tiene el usuario (checkout y carrito). */
    @Query("select l.game.id from LibraryEntry l where l.user.id = :userId and l.game.id in :gameIds")
    Set<Long> findOwnedGameIds(Long userId, Collection<Long> gameIds);
}
