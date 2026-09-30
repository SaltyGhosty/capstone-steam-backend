package com.capstone.steam.game.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;

import com.capstone.steam.game.Game;
import com.capstone.steam.genre.dto.GenreResponse;

/** Tarjeta del catálogo. */
public record GameSummaryResponse(
        Long id,
        String title,
        String slug,
        String coverUrl,
        BigDecimal price,
        int discountPercent,
        BigDecimal finalPrice,
        LocalDate releaseDate,
        double averageRating,
        int ratingCount,
        List<GenreResponse> genres,
        String developerName) {

    public static GameSummaryResponse from(Game game) {
        return new GameSummaryResponse(
                game.getId(),
                game.getTitle(),
                game.getSlug(),
                game.getCoverUrl(),
                game.getPrice(),
                game.getDiscountPercent(),
                game.getFinalPrice(),
                game.getReleaseDate(),
                roundRating(game.getAverageRating()),
                game.getRatingCount(),
                genres(game),
                game.getDeveloper().getDisplayName());
    }

    static List<GenreResponse> genres(Game game) {
        return game.getGenres().stream()
                .map(GenreResponse::from)
                .sorted(Comparator.comparing(GenreResponse::name))
                .toList();
    }

    static double roundRating(double rating) {
        return Math.round(rating * 10) / 10.0;
    }
}
