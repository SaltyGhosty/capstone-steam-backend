package com.capstone.steam.notification.event;

/** Usuario que tiene en su wishlist un juego que acaba de entrar en oferta. */
public record SaleRecipient(String email, String displayName, String locale) {
}
