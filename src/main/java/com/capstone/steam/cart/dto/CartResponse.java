package com.capstone.steam.cart.dto;

import java.math.BigDecimal;
import java.util.List;

public record CartResponse(List<CartItemResponse> items, int itemCount, BigDecimal total) {

    public static CartResponse of(List<CartItemResponse> items) {
        BigDecimal total = items.stream()
                .filter(CartItemResponse::available)
                .map(CartItemResponse::finalPrice)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return new CartResponse(items, items.size(), total);
    }
}
