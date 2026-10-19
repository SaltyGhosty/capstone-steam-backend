package com.capstone.steam.game.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Set;

import org.hibernate.validator.constraints.URL;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * Alta y edición (PUT, reemplazo completo) de un juego por su developer.
 * {@code version}: opcional en el PUT; si se envía y no coincide con la actual -> 409.
 */
public record GameRequest(
        @NotBlank @Size(max = 150) String title,
        @Size(max = 10000) String description,
        @Size(max = 10000) String descriptionEn,
        @Size(max = 10000) String descriptionIt,
        @NotNull @DecimalMin("0.00") @Digits(integer = 8, fraction = 2) BigDecimal price,
        @Min(0) @Max(100) Integer discountPercent,
        LocalDate releaseDate,
        @URL(regexp = "(?i)^https?://.+") @Size(max = 500) String coverUrl,
        @Positive Integer steamAppId,
        Set<Long> genreIds,
        Long version) {
}
