package com.capstone.steam.notification.event;

/**
 * Messaggio del form di assistenza. Si pubblica fuori da una transazione (non si salva nulla nel
 * database), quindi il listener lo riceve subito. {@code displayName} e {@code username} sono null se chi
 * scrive non ha sessione. Solo dati primitivi, mai entità.
 */
public record SupportRequestEvent(String fromEmail, String displayName, String username, String subject,
        String message, String locale) {
}
