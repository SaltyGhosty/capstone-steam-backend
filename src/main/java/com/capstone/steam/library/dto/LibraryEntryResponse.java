package com.capstone.steam.library.dto;

import java.time.Instant;

import com.capstone.steam.game.Game;
import com.capstone.steam.library.LibraryEntry;

/** {@code orderId} è null se il gioco è stato aggiunto senza acquisto (regalo di un admin). {@code developerName} è null
 * se lo studio è eliminato ({@code developerDeleted}). */
public record LibraryEntryResponse(
        Long gameId,
        String title,
        String slug,
        String coverUrl,
        Instant acquiredAt,
        int playtimeMinutes,
        Long orderId,
        String developerName,
        boolean developerDeleted) {

    public static LibraryEntryResponse from(LibraryEntry entry) {
        Game game = entry.getGame();
        Long orderId = entry.getOrder() == null ? null : entry.getOrder().getId();
        return new LibraryEntryResponse(game.getId(), game.getTitle(), game.getSlug(), game.getCoverUrl(),
                entry.getAcquiredAt(), entry.getPlaytimeMinutes(), orderId, game.getDeveloper().getDisplayName(),
                game.getDeveloper().isDeleted());
    }
}
