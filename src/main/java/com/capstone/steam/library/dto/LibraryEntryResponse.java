package com.capstone.steam.library.dto;

import java.time.Instant;

import com.capstone.steam.game.Game;
import com.capstone.steam.library.LibraryEntry;

/** {@code orderId} es null si el juego se añadió sin compra (regalo de un admin). */
public record LibraryEntryResponse(
        Long gameId,
        String title,
        String slug,
        String coverUrl,
        Instant acquiredAt,
        int playtimeMinutes,
        Long orderId) {

    public static LibraryEntryResponse from(LibraryEntry entry) {
        Game game = entry.getGame();
        Long orderId = entry.getOrder() == null ? null : entry.getOrder().getId();
        return new LibraryEntryResponse(game.getId(), game.getTitle(), game.getSlug(), game.getCoverUrl(),
                entry.getAcquiredAt(), entry.getPlaytimeMinutes(), orderId);
    }
}
