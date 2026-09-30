package com.capstone.steam.game.search;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/**
 * Filtros opcionales del catálogo; se ligan desde la query string
 * (p. ej. {@code ?q=zelda&genres=rpg,action&maxPrice=20&onSale=true}). null = sin filtro.
 *
 * @param genres  slugs de género; el juego debe tener AL MENOS uno
 * @param minPrice/maxPrice se aplican al precio final (con descuento)
 */
public record GameSearchCriteria(
        @Size(max = 100) String q,
        @Size(max = 20) List<String> genres,
        @PositiveOrZero BigDecimal minPrice,
        @PositiveOrZero BigDecimal maxPrice,
        Boolean onSale,
        Long developerId,
        LocalDate releasedAfter,
        LocalDate releasedBefore,
        @DecimalMin("1.0") @DecimalMax("5.0") Double minRating) {

    public static GameSearchCriteria empty() {
        return new GameSearchCriteria(null, null, null, null, null, null, null, null, null);
    }
}
