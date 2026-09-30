package com.capstone.steam.library.dto;

/** Estado del usuario respecto a un juego: lo usa la ficha del juego para pintar sus botones. */
public record GameOwnershipResponse(
        Long gameId,
        boolean owned,
        boolean inCart,
        boolean inWishlist,
        boolean reviewed) {
}
