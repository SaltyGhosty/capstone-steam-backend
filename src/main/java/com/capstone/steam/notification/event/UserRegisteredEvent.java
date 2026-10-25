package com.capstone.steam.notification.event;

/**
 * Si pubblica dentro la transazione di registrazione; il listener delle email
 * la consuma in AFTER_COMMIT. Solo dati primitivi, mai entità.
 *
 * @param locale            lingua dell'email (es, en, it), letta da {@code User} dentro la transazione
 * @param verificationToken token di verifica in chiaro per il link dell'email; {@code null} se la
 *                          verifica dell'email è disattivata (esce il benvenuto di sempre)
 * @param username          l'account da confermare (l'email di verifica lo nomina)
 */
public record UserRegisteredEvent(Long userId, String email, String displayName, String locale,
        String verificationToken, String username) {
}
