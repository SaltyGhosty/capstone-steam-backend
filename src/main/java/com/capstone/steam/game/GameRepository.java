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

    /** Carga los géneros de una página de juegos en una sola query (evita el N+1 del catálogo). */
    @EntityGraph(attributePaths = {"genres", "developer"})
    List<Game> findWithDetailsByIdIn(Collection<Long> ids);

    Page<Game> findByDeveloperId(Long developerId, Pageable pageable);

    Page<Game> findByStatus(GameStatus status, Pageable pageable);

    boolean existsBySteamAppId(Integer steamAppId);

    Optional<Game> findBySteamAppId(Integer steamAppId);

    /** Los juegos enlazados con Steam, cuyo precio se sincroniza. */
    List<Game> findBySteamAppIdIsNotNull();

    boolean existsBySlug(String slug);

    // ------------------------------------------------------------------
    // UPDATE atómicos escritos a mano: la base de datos hace la suma,
    // así que no hay read-modify-write ni actualizaciones perdidas con
    // compras o reseñas concurrentes. No incrementan @Version a propósito:
    // un contador no debe invalidar la edición en curso de un developer.
    // ------------------------------------------------------------------

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update Game g set g.salesCount = g.salesCount + 1 where g.id in :gameIds")
    int incrementSalesCount(Collection<Long> gameIds);

    /**
     * Crear una reseña: (1 si es positiva y 0 si no, +1). Borrarla: (-1 o 0, -1).
     * Editarla cambiando el voto: (+1 o -1, 0).
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update Game g
               set g.positiveCount = g.positiveCount + :positiveDelta,
                   g.reviewCount = g.reviewCount + :countDelta
             where g.id = :gameId""")
    int applyReviewDelta(Long gameId, int positiveDelta, int countDelta);

    /** Descuento masivo sobre los juegos publicados de un developer. Esto sí es una edición, así que sube la versión. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update Game g
               set g.discountPercent = :percent,
                   g.version = g.version + 1
             where g.developer.id = :developerId
               and g.status = :status""")
    int bulkDiscountForDeveloper(Long developerId, int percent, GameStatus status);
}
