package com.capstone.steam.steamstore;

import java.time.LocalDate;
import java.util.List;

/**
 * Scheda di un gioco su Steam, in una lingua.
 *
 * @param description descrizione breve, già in testo semplice
 * @param genreIds    id di genere di Steam ("1" = Action...)
 * @param price       null se Steam non dà un prezzo (gioco gratuito o ancora senza prezzo)
 */
public record SteamApp(int appId, String name, String description, LocalDate releaseDate, List<String> genreIds,
        boolean free, SteamPrice price) {
}
