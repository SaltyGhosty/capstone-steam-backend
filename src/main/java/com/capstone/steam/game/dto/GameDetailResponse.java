package com.capstone.steam.game.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import com.capstone.steam.game.Game;
import com.capstone.steam.game.GameStatus;
import com.capstone.steam.genre.dto.GenreResponse;

/** Ficha completa del juego; {@code version} se reenvía en el PUT para el bloqueo optimista. */
public record GameDetailResponse(
        Long id,
        String title,
        String slug,
        String description,
        String coverUrl,
        BigDecimal price,
        int discountPercent,
        BigDecimal finalPrice,
        LocalDate releaseDate,
        GameStatus status,
        double averageRating,
        int ratingCount,
        long salesCount,
        List<GenreResponse> genres,
        DeveloperInfo developer,
        Instant createdAt,
        Instant updatedAt,
        Long version) {

    public record DeveloperInfo(Long id, String displayName) {
    }

    public static GameDetailResponse from(Game game) {
        return new GameDetailResponse(
                game.getId(),
                game.getTitle(),
                game.getSlug(),
                game.getDescription(),
                game.getCoverUrl(),
                game.getPrice(),
                game.getDiscountPercent(),
                game.getFinalPrice(),
                game.getReleaseDate(),
                game.getStatus(),
                GameSummaryResponse.roundRating(game.getAverageRating()),
                game.getRatingCount(),
                game.getSalesCount(),
                GameSummaryResponse.genres(game),
                new DeveloperInfo(game.getDeveloper().getId(), game.getDeveloper().getDisplayName()),
                game.getCreatedAt(),
                game.getUpdatedAt(),
                game.getVersion());
    }
}
