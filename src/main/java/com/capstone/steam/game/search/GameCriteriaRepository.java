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
 * Búsqueda del catálogo con Criteria API: los predicados se construyen solo para
 * los filtros presentes. Siempre filtra por juegos PUBLISHED.
 *
 * <p>Se ejecutan dos queries (página + count) con los mismos predicados. Los géneros
 * y el developer de la página se cargan después por lotes (default_batch_fetch_size),
 * así que no hay fetch join sobre colecciones ni paginación en memoria.
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
            // lower(title) LIKE '%q%': en PostgreSQL lo acelera el índice GIN de trigramas (V4)
            String pattern = "%" + escapeLike(c.q().trim().toLowerCase(Locale.ROOT)) + "%";
            predicates.add(cb.like(cb.lower(game.get("title")), pattern, '\\'));
        }

        if (c.genres() != null && !c.genres().isEmpty()) {
            // Subquery en vez de join: sin filas duplicadas, sin DISTINCT y la paginación sigue en la BD
            Subquery<Long> withGenre = query.subquery(Long.class);
            Root<Game> sub = withGenre.from(Game.class);
            Join<Game, Genre> genre = sub.join("genres");
            withGenre.select(sub.get("id")).where(genre.get("slug").in(c.genres()));
            predicates.add(game.get("id").in(withGenre));
        }

        // precio_final * 100 = price * (100 - discount): se compara sin dividir para no redondear en SQL
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

        if (c.minRating() != null) {
            // media >= min  <=>  rating_sum >= min * rating_count (sin división: nunca divide entre 0)
            predicates.add(cb.gt(game.get("ratingCount"), 0));
            predicates.add(cb.ge(game.get("ratingSum"),
                    cb.prod(game.<Number>get("ratingCount"), cb.literal(c.minRating()))));
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
                // media = sum * 1.0 / nullif(count, 0): los juegos sin reseñas (NULL) van al final
                Expression<Number> average = cb.quot(
                        cb.prod(game.<Number>get("ratingSum"), cb.literal(BigDecimal.ONE)),
                        cb.nullif(game.<Integer>get("ratingCount"), 0));
                orders.add(nullsLast(cb, average));
                orders.add(cb.desc(average));
                orders.add(cb.desc(game.get("ratingCount")));
            }
            case BEST_SELLING -> orders.add(cb.desc(game.get("salesCount")));
            case TITLE -> orders.add(cb.asc(cb.lower(game.get("title"))));
        }
        orders.add(cb.asc(game.get("id"))); // desempate estable para la paginación
        return orders;
    }

    /**
     * ORDER BY (CASE WHEN x IS NULL THEN 1 ELSE 0 END): manda los NULL al final.
     * Portable; el Nulls.LAST de JPA 3.2 no se traduce a SQL de forma fiable en todos los dialectos.
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
