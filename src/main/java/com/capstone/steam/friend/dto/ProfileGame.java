package com.capstone.steam.friend.dto;

import com.capstone.steam.game.Game;
import com.capstone.steam.library.LibraryEntry;

/** Un juego de la biblioteca de otro jugador: sin precio de compra ni pedido. */
public record ProfileGame(Long gameId, String title, String slug, String coverUrl, int playtimeMinutes) {

    public static ProfileGame from(LibraryEntry entry) {
        Game game = entry.getGame();
        return new ProfileGame(game.getId(), game.getTitle(), game.getSlug(), game.getCoverUrl(),
                entry.getPlaytimeMinutes());
    }
}
