package com.capstone.steam.review.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * {@code gameId} solo se usa al crear; en el PUT se ignora. {@code language} (es, en o it) es el
 * idioma de la interfaz; si no llega, se usa el idioma del autor.
 */
public record ReviewRequest(
        Long gameId,
        @NotNull Boolean recommended,
        @Size(max = 5000) String body,
        @Pattern(regexp = "^(es|en|it)$", message = "{validation.locale}") String language) {
}
