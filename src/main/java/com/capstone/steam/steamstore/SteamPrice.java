package com.capstone.steam.steamstore;

import java.math.BigDecimal;

/**
 * Precio de Steam para un país: {@code initial} es el precio sin rebaja y {@code discountPercent}
 * la rebaja actual (0 si no hay oferta).
 */
public record SteamPrice(String currency, BigDecimal initial, BigDecimal finalPrice, int discountPercent) {
}
