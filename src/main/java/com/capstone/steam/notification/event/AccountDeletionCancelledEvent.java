package com.capstone.steam.notification.event;

/** Si pubblica dentro la transazione che annulla una cancellazione in sospeso; l'email esce in AFTER_COMMIT. */
public record AccountDeletionCancelledEvent(String email, String displayName, String locale) {
}
