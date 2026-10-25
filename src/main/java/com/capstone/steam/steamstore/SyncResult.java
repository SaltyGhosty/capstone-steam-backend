package com.capstone.steam.steamstore;

/**
 * Riepilogo di una sincronizzazione dei prezzi.
 *
 * @param checked  giochi collegati a Steam
 * @param updated  giochi il cui prezzo o sconto è cambiato
 * @param newSales giochi pubblicati che sono andati in offerta (o l'hanno migliorata)
 * @param notified avvisi inviati alle wishlist
 */
public record SyncResult(int checked, int updated, int newSales, int notified) {
}
