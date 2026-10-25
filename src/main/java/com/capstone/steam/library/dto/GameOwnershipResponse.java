package com.capstone.steam.library.dto;

/** Stato dell'utente rispetto a un gioco: lo usa la scheda del gioco per disegnare i suoi pulsanti. */
public record GameOwnershipResponse(
        Long gameId,
        boolean owned,
        boolean inCart,
        boolean inWishlist,
        boolean reviewed) {
}
