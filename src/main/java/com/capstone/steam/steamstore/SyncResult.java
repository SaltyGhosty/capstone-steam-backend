package com.capstone.steam.steamstore;

/**
 * Resumen de una sincronización de precios.
 *
 * @param checked  juegos enlazados con Steam
 * @param updated  juegos cuyo precio o descuento ha cambiado
 * @param newSales juegos publicados que han entrado en oferta (o la han mejorado)
 * @param notified avisos enviados a wishlists
 */
public record SyncResult(int checked, int updated, int newSales, int notified) {
}
