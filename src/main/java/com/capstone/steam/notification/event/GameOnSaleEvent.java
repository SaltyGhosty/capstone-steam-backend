package com.capstone.steam.notification.event;

import java.math.BigDecimal;
import java.util.List;

/**
 * Un gioco nella wishlist di qualcuno è entrato in offerta (o l'ha migliorata) sincronizzando con Steam.
 * I destinatari si leggono dentro la transazione; le email escono dopo il commit.
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
