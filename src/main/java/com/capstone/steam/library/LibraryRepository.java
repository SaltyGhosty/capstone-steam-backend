package com.capstone.steam.library;

import java.util.Collection;
import java.util.Optional;
import java.util.Set;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface LibraryRepository extends JpaRepository<LibraryEntry, Long> {

    @Query(value = "select l from LibraryEntry l join fetch l.game where l.user.id = :userId",
            countQuery = "select count(l) from LibraryEntry l where l.user.id = :userId")
    Page<LibraryEntry> findByUserId(Long userId, Pageable pageable);

    boolean existsByUserIdAndGameId(Long userId, Long gameId);

    /** De entre gameIds, cuáles ya tiene el usuario (checkout y carrito). */
    @Query("select l.game.id from LibraryEntry l where l.user.id = :userId and l.game.id in :gameIds")
    Set<Long> findOwnedGameIds(Long userId, Collection<Long> gameIds);

    @Query("select l from LibraryEntry l join fetch l.game where l.user.id = :userId and l.game.id = :gameId")
    Optional<LibraryEntry> findByUserIdAndGameId(Long userId, Long gameId);

    /** Suma minutos jugados sin leer antes la fila: dos sesiones simultáneas no se pisan. 0 = no lo tiene. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update LibraryEntry l
               set l.playtimeMinutes = l.playtimeMinutes + :minutes
             where l.user.id = :userId and l.game.id = :gameId""")
    int addPlaytime(Long userId, Long gameId, int minutes);
}
