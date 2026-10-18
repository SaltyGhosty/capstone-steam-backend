package com.capstone.steam.notification.event;

import java.math.BigDecimal;
import java.util.List;

/**
 * Un juego de la wishlist de alguien ha entrado en oferta (o la ha mejorado) al sincronizar con Steam.
 * Los destinatarios se leen dentro de la transacción; los correos salen después del commit.
 */
public record GameOnSaleEvent(
        Long gameId,
        String title,
        String slug,
        String coverUrl,
        BigDecimal price,
        BigDecimal finalPrice,
        int discountPercent,
        List<SaleRecipient> recipients) {
}
