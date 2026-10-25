package com.capstone.steam.notification.event;

/** Utente che ha nella wishlist un gioco appena entrato in offerta. */
public record SaleRecipient(String email, String displayName, String locale) {
}
