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

    /** Tra gameIds, quali l'utente possiede già (checkout e carrello). */
    @Query("select l.game.id from LibraryEntry l where l.user.id = :userId and l.game.id in :gameIds")
    Set<Long> findOwnedGameIds(Long userId, Collection<Long> gameIds);

    /** Id di tutti i giochi della libreria dell'utente (l'assistente non li raccomanda). */
    @Query("select l.game.id from LibraryEntry l where l.user.id = :userId")
    Set<Long> findGameIdsByUserId(Long userId);

    @Query("select l from LibraryEntry l join fetch l.game where l.user.id = :userId and l.game.id = :gameId")
    Optional<LibraryEntry> findByUserIdAndGameId(Long userId, Long gameId);

    /** Somma i minuti giocati senza leggere prima la riga: due sessioni simultanee non si sovrascrivono. 0 = non lo possiede. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update LibraryEntry l
               set l.playtimeMinutes = l.playtimeMinutes + :minutes
             where l.user.id = :userId and l.game.id = :gameId""")
    int addPlaytime(Long userId, Long gameId, int minutes);
}
