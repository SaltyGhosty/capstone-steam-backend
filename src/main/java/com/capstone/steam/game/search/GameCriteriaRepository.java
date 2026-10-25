package com.capstone.steam.game.search;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;

import com.capstone.steam.game.Game;
import com.capstone.steam.game.GameStatus;
import com.capstone.steam.game.ReviewScore;
import com.capstone.steam.genre.Genre;

import jakarta.persistence.EntityManager;
import jakarta.persistence.criteria.AbstractQuery;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.Order;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;

/**
 * Ricerca nel catalogo con Criteria API: i predicati si costruiscono solo per
 * i filtri presenti. Filtra sempre per giochi PUBLISHED.
 *
 * <p>Si eseguono due query (pagina + count) con gli stessi predicati. I generi
 * e il developer della pagina si caricano poi in blocco (default_batch_fetch_size),
 * quindi niente fetch join su collezioni né paginazione in memoria.
 */
@Repository
public class GameCriteriaRepository {

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private final EntityManager em;

    public GameCriteriaRepository(EntityManager em) {
        this.em = em;
    }

    public Page<Game> search(GameSearchCriteria criteria, GameSort sort, PageRequest pageRequest) {
        CriteriaBuilder cb = em.getCriteriaBuilder();

        CriteriaQuery<Game> query = cb.createQuery(Game.class);
        Root<Game> game = query.from(Game.class);
        query.select(game)
                .where(predicates(cb, query, game, criteria))
                .orderBy(orderBy(cb, game, sort));
        List<Game> content = em.createQuery(query)
                .setFirstResult((int) pageRequest.getOffset())
                .setMaxResults(pageRequest.getPageSize())
                .getResultList();

        CriteriaQuery<Long> countQuery = cb.createQuery(Long.class);
        Root<Game> countRoot = countQuery.from(Game.class);
        countQuery.select(cb.count(countRoot)).where(predicates(cb, countQuery, countRoot, criteria));
        long total = em.createQuery(countQuery).getSingleResult();

        return new PageImpl<>(content, pageRequest, total);
    }

    private Predicate[] predicates(CriteriaBuilder cb, AbstractQuery<?> query, Root<Game> game,
            GameSearchCriteria c) {
        List<Predicate> predicates = new ArrayList<>();
        predicates.add(cb.equal(game.get("status"), GameStatus.PUBLISHED));

        if (c.q() != null && !c.q().isBlank()) {
            // lower(title) LIKE '%q%': su PostgreSQL lo accelera l'indice GIN di trigrammi (V4)
            String pattern = "%" + escapeLike(c.q().trim().toLowerCase(Locale.ROOT)) + "%";
            predicates.add(cb.like(cb.lower(game.get("title")), pattern, '\\'));
        }

        if (c.genres() != null && !c.genres().isEmpty()) {
            // Subquery invece di join: niente righe duplicate, niente DISTINCT e la paginazione resta nel DB
            Subquery<Long> withGenre = query.subquery(Long.class);
            Root<Game> sub = withGenre.from(Game.class);
            Join<Game, Genre> genre = sub.join("genres");
            withGenre.select(sub.get("id")).where(genre.get("slug").in(c.genres()));
            predicates.add(game.get("id").in(withGenre));
        }

        // prezzo_finale * 100 = price * (100 - discount): si confronta senza dividere per non arrotondare in SQL
        Expression<Number> finalPriceTimes100 = finalPriceTimes100(cb, game);
        if (c.minPrice() != null) {
            predicates.add(cb.ge(finalPriceTimes100, c.minPrice().multiply(HUNDRED)));
        }
        if (c.maxPrice() != null) {
            predicates.add(cb.le(finalPriceTimes100, c.maxPrice().multiply(HUNDRED)));
        }

        if (Boolean.TRUE.equals(c.onSale())) {
            predicates.add(cb.gt(game.get("discountPercent"), 0));
        }

        if (c.developerId() != null) {
            predicates.add(cb.equal(game.get("developer").get("id"), c.developerId()));
        }

        if (c.releasedAfter() != null) {
            predicates.add(cb.greaterThanOrEqualTo(game.get("releaseDate"), c.releasedAfter()));
        }
        if (c.releasedBefore() != null) {
            predicates.add(cb.lessThanOrEqualTo(game.get("releaseDate"), c.releasedBefore()));
        }

        if (c.minPositivePercent() != null) {
            // Con meno di 10 recensioni non c'è punteggio e il gioco non passa il filtro.
            // positive / totale >= min / 100  <=>  positive * 100 >= min * totale (senza divisione: mai divide per 0)
            predicates.add(cb.ge(game.<Integer>get("reviewCount"), ReviewScore.MIN_REVIEWS));
            predicates.add(cb.ge(cb.prod(game.<Number>get("positiveCount"), cb.literal(100)),
                    cb.prod(game.<Number>get("reviewCount"), cb.literal(c.minPositivePercent()))));
        }

        return predicates.toArray(Predicate[]::new);
    }

    private List<Order> orderBy(CriteriaBuilder cb, Root<Game> game, GameSort sort) {
        List<Order> orders = new ArrayList<>();
        switch (sort) {
            case NEWEST -> {
                orders.add(nullsLast(cb, game.get("releaseDate")));
                orders.add(cb.desc(game.get("releaseDate")));
            }
            case PRICE_ASC -> orders.add(cb.asc(finalPriceTimes100(cb, game)));
            case PRICE_DESC -> orders.add(cb.desc(finalPriceTimes100(cb, game)));
            case TOP_RATED -> {
                // % positiva = positive * 1.0 / nullif(totale, 0): i giochi senza recensioni (NULL) vanno in fondo
                Expression<Number> positiveRatio = cb.quot(
                        cb.prod(game.<Number>get("positiveCount"), cb.literal(BigDecimal.ONE)),
                        cb.nullif(game.<Integer>get("reviewCount"), 0));
                // prima i giochi con punteggio (10+ recensioni): 1 recensione al 100 % non batte 30 al 95 %
                orders.add(cb.asc(cb.<Integer>selectCase()
                        .when(cb.ge(game.<Integer>get("reviewCount"), ReviewScore.MIN_REVIEWS), 0)
                        .otherwise(1)));
                orders.add(nullsLast(cb, positiveRatio));
                orders.add(cb.desc(positiveRatio));
                orders.add(cb.desc(game.get("reviewCount")));
            }
            case BEST_SELLING -> orders.add(cb.desc(game.get("salesCount")));
            case TITLE -> orders.add(cb.asc(cb.lower(game.get("title"))));
        }
        orders.add(cb.asc(game.get("id"))); // spareggio stabile per la paginazione
        return orders;
    }

    /**
     * ORDER BY (CASE WHEN x IS NULL THEN 1 ELSE 0 END): manda i NULL in fondo.
     * Portabile; il Nulls.LAST di JPA 3.2 non si traduce in SQL in modo affidabile in tutti i dialetti.
     */
    private static Order nullsLast(CriteriaBuilder cb, Expression<?> expression) {
        return cb.asc(cb.<Integer>selectCase().when(cb.isNull(expression), 1).otherwise(0));
    }

    private static Expression<Number> finalPriceTimes100(CriteriaBuilder cb, Root<Game> game) {
        return cb.prod(game.<Number>get("price"), cb.diff(cb.literal(100), game.<Integer>get("discountPercent")));
    }

    private static String escapeLike(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
