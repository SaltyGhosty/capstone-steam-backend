package com.capstone.steam.game;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface GameRepository extends JpaRepository<Game, Long> {

    @EntityGraph(attributePaths = {"genres", "developer"})
    Optional<Game> findBySlug(String slug);

    @EntityGraph(attributePaths = {"genres", "developer"})
    Optional<Game> findWithDetailsById(Long id);

    /** Carica i generi di una pagina di giochi in una sola query (evita l'N+1 del catalogo). */
    @EntityGraph(attributePaths = {"genres", "developer"})
    List<Game> findWithDetailsByIdIn(Collection<Long> ids);

    /** Tutti i giochi di uno stato con generi e developer, per il prompt dell'assistente. */
    @EntityGraph(attributePaths = {"genres", "developer"})
    List<Game> findWithDetailsByStatusOrderByIdAsc(GameStatus status);

    Page<Game> findByDeveloperId(Long developerId, Pageable pageable);

    /** Tutti i giochi di un developer, in qualsiasi stato (all'anonimizzazione del suo account). */
    List<Game> findByDeveloperId(Long developerId);

    /** Se un carrello, wishlist, libreria, ordine o recensione punta al gioco (allora non si può eliminare). */
    @Query("""
            select case when exists (select 1 from LibraryEntry l where l.game.id = :gameId)
                          or exists (select 1 from OrderItem i where i.game.id = :gameId)
                          or exists (select 1 from CartItem c where c.game.id = :gameId)
                          or exists (select 1 from WishlistItem w where w.game.id = :gameId)
                          or exists (select 1 from Review r where r.game.id = :gameId)
                        then true else false end
            from Game g where g.id = :gameId""")
    boolean isReferenced(Long gameId);

    Page<Game> findByStatus(GameStatus status, Pageable pageable);

    boolean existsBySteamAppId(Integer steamAppId);

    Optional<Game> findBySteamAppId(Integer steamAppId);

    /** I giochi collegati a Steam, il cui prezzo viene sincronizzato. */
    List<Game> findBySteamAppIdIsNotNull();

    boolean existsBySlug(String slug);

    // UPDATE atomici scritti a mano: è il database a fare la somma,
    // quindi niente read-modify-write né aggiornamenti persi con
    // acquisti o recensioni concorrenti. Non incrementano @Version di proposito:
    // un contatore non deve invalidare la modifica in corso di un developer.

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update Game g set g.salesCount = g.salesCount + 1 where g.id in :gameIds")
    int incrementSalesCount(Collection<Long> gameIds);

    /**
     * Creare una recensione: (1 se positiva e 0 altrimenti, +1). Eliminarla: (-1 o 0, -1).
     * Modificarla cambiando il voto: (+1 o -1, 0).
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update Game g
               set g.positiveCount = g.positiveCount + :positiveDelta,
                   g.reviewCount = g.reviewCount + :countDelta
             where g.id = :gameId""")
    int applyReviewDelta(Long gameId, int positiveDelta, int countDelta);

    /** Sconto massivo sui giochi pubblicati di un developer. Questa sì è una modifica, quindi incrementa la versione. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update Game g
               set g.discountPercent = :percent,
                   g.version = g.version + 1
             where g.developer.id = :developerId
               and g.status = :status""")
    int bulkDiscountForDeveloper(Long developerId, int percent, GameStatus status);
}
