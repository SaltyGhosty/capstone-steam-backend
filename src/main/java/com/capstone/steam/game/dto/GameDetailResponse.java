package com.capstone.steam.game.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import org.springframework.context.i18n.LocaleContextHolder;

import com.capstone.steam.game.Game;
import com.capstone.steam.game.GameStatus;
import com.capstone.steam.game.ReviewScore;
import com.capstone.steam.genre.dto.GenreResponse;

/**
 * Ficha completa del juego; {@code version} se reenvía en el PUT para el bloqueo optimista.
 * {@code description} va en el idioma de la petición; {@code descriptions} trae las tres tal cual
 * (las usa el formulario del developer).
 */
public record GameDetailResponse(
        Long id,
        String title,
        String slug,
        String description,
        Descriptions descriptions,
        String coverUrl,
        Integer steamAppId,
        Instant priceSyncedAt,
        BigDecimal price,
        int discountPercent,
        BigDecimal finalPrice,
        LocalDate releaseDate,
        GameStatus status,
        int reviewCount,
        int positivePercent,
        ReviewScore reviewScore,
        long salesCount,
        List<GenreResponse> genres,
        DeveloperInfo developer,
        Instant createdAt,
        Instant updatedAt,
        Long version) {

    public record DeveloperInfo(Long id, String displayName) {
    }

    public record Descriptions(String es, String en, String it) {
    }

    public static GameDetailResponse from(Game game) {
        return new GameDetailResponse(
                game.getId(),
                game.getTitle(),
                game.getSlug(),
                game.getDescription(LocaleContextHolder.getLocale()),
                new Descriptions(game.getDescription(), game.getDescriptionEn(), game.getDescriptionIt()),
                game.getCoverUrl(),
                game.getSteamAppId(),
                game.getPriceSyncedAt(),
                game.getPrice(),
                game.getDiscountPercent(),
                game.getFinalPrice(),
                game.getReleaseDate(),
                game.getStatus(),
                game.getReviewCount(),
                game.getPositivePercent(),
                game.getReviewScore(),
                game.getSalesCount(),
                GameSummaryResponse.genres(game),
                new DeveloperInfo(game.getDeveloper().getId(), game.getDeveloper().getDisplayName()),
                game.getCreatedAt(),
                game.getUpdatedAt(),
                game.getVersion());
    }
}
