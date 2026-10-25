package com.capstone.steam.game.search;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/**
 * Filtri opzionali del catalogo; si legano dalla query string
 * (es. {@code ?q=zelda&genres=rpg,action&maxPrice=20&onSale=true}). null = nessun filtro.
 *
 * @param genres  slug di genere; il gioco deve averne ALMENO uno
 * @param minPrice/maxPrice si applicano al prezzo finale (scontato)
 * @param minPositivePercent percentuale minima di recensioni positive (0-100)
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
        @Min(0) @Max(100) Integer minPositivePercent) {

    public static GameSearchCriteria empty() {
        return new GameSearchCriteria(null, null, null, null, null, null, null, null, null);
    }
}
