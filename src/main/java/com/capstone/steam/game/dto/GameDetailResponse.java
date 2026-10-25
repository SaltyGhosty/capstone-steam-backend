package com.capstone.steam.game.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import com.capstone.steam.common.i18n.Messages;
import com.capstone.steam.game.Game;
import com.capstone.steam.game.GameStatus;
import com.capstone.steam.game.ReviewScore;
import com.capstone.steam.genre.dto.GenreResponse;

/**
 * Scheda completa del gioco; {@code version} viene rinviata nel PUT per il lock ottimistico.
 * {@code description} è nella lingua della richiesta; {@code descriptions} porta tutte e tre così come sono
 * (le usa il modulo del developer).
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

    /** {@code displayName} è null e {@code deleted} true se lo studio ha eliminato l'account; il testo lo mette il frontend. */
    public record DeveloperInfo(Long id, String displayName, boolean deleted) {
    }

    public record Descriptions(String es, String en, String it) {
    }

    public static GameDetailResponse from(Game game) {
        return new GameDetailResponse(
                game.getId(),
                game.getTitle(),
                game.getSlug(),
                game.getDescription(Messages.currentLocale()),
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
                new DeveloperInfo(game.getDeveloper().getId(), game.getDeveloper().getDisplayName(),
                        game.getDeveloper().isDeleted()),
                game.getCreatedAt(),
                game.getUpdatedAt(),
                game.getVersion());
    }
}
