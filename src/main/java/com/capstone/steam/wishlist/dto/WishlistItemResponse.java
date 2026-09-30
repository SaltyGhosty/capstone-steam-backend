package com.capstone.steam.wishlist.dto;

import java.math.BigDecimal;
import java.time.Instant;

import com.capstone.steam.game.Game;
import com.capstone.steam.wishlist.WishlistItem;

/** {@code onSale} permite al frontend destacar los juegos deseados que están rebajados. */
public record WishlistItemResponse(
        Long gameId,
        String title,
        String slug,
        String coverUrl,
        BigDecimal price,
        int discountPercent,
        BigDecimal finalPrice,
        boolean onSale,
        boolean available,
        Instant addedAt) {

    public static WishlistItemResponse from(WishlistItem item) {
        Game game = item.getGame();
        return new WishlistItemResponse(game.getId(), game.getTitle(), game.getSlug(), game.getCoverUrl(),
                game.getPrice(), game.getDiscountPercent(), game.getFinalPrice(), game.getDiscountPercent() > 0,
                game.isPublished(), item.getAddedAt());
    }
}
