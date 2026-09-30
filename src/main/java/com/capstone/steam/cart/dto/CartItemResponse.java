package com.capstone.steam.cart.dto;

import java.math.BigDecimal;
import java.time.Instant;

import com.capstone.steam.cart.CartItem;
import com.capstone.steam.game.Game;

/**
 * @param available false si el juego dejó de estar publicado después de añadirlo:
 *                  se muestra, pero no cuenta en el total ni se podrá comprar
 */
public record CartItemResponse(
        Long gameId,
        String title,
        String slug,
        String coverUrl,
        BigDecimal price,
        int discountPercent,
        BigDecimal finalPrice,
        boolean available,
        Instant addedAt) {

    public static CartItemResponse from(CartItem item) {
        Game game = item.getGame();
        return new CartItemResponse(game.getId(), game.getTitle(), game.getSlug(), game.getCoverUrl(),
                game.getPrice(), game.getDiscountPercent(), game.getFinalPrice(), game.isPublished(),
                item.getAddedAt());
    }
}
