package com.capstone.steam.notification.event;

import java.time.Instant;

import com.capstone.steam.user.DeletionRequester;

/**
 * Si pubblica dentro la transazione che programma la cancellazione; il listener delle email la consuma in AFTER_COMMIT.
 * Solo dati primitivi, mai entità.
 *
 * @param scheduledAt quando l'account verrà anonimizzato
 * @param requestedBy chi ha chiesto la cancellazione (decide se l'email offre di recuperarlo)
 */
public record AccountDeletionScheduledEvent(String email, String displayName, String locale, Instant scheduledAt,
        DeletionRequester requestedBy) {
}
