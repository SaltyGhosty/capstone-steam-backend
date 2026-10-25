package com.capstone.steam.notification.event;

/**
 * Si pubblica dentro la transazione che anonimizza l'account, con i dati che aveva PRIMA di anonimizzare
 * (dopo l'email non esiste più). L'email esce in AFTER_COMMIT. Solo dati primitivi, mai entità.
 */
public record AccountDeletedEvent(String email, String displayName, String locale) {
}
