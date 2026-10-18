package com.capstone.steam.steamstore;

import java.time.LocalDate;
import java.util.List;

/**
 * Ficha de un juego en Steam, en un idioma.
 *
 * @param description descripción corta, ya en texto plano
 * @param genreIds    ids de género de Steam ("1" = Action...)
 * @param price       null si Steam no da precio (juego gratuito o aún sin precio)
 */
public record SteamApp(int appId, String name, String description, LocalDate releaseDate, List<String> genreIds,
        boolean free, SteamPrice price) {
}
