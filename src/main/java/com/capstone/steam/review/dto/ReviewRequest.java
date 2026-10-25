package com.capstone.steam.review.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * {@code gameId} si usa solo alla creazione; nel PUT viene ignorato. {@code language} (es, en o it) è la
 * lingua dell'interfaccia; se non arriva, si usa la lingua dell'autore.
 */
public record ReviewRequest(
        Long gameId,
        @NotNull Boolean recommended,
        @Size(max = 5000) String body,
        @Pattern(regexp = "^(es|en|it)$", message = "{validation.locale}") String language) {
}
