package com.capstone.steam.steamstore;

import java.math.BigDecimal;

/**
 * Prezzo di Steam per un paese: {@code initial} è il prezzo senza sconto e {@code discountPercent}
 * lo sconto attuale (0 se non c'è offerta).
 */
public record SteamPrice(String currency, BigDecimal initial, BigDecimal finalPrice, int discountPercent) {
}
