package com.capstone.steam.notification.event;

/**
 * Reinvio del link di verifica. Porta il token in chiaro fino al listener delle email
 * (AFTER_COMMIT); non viene mai salvato né scritto nel log con l'email attiva.
 *
 * @param username l'account da confermare: l'email lo nomina perché chi non l'ha chiesto lo riconosca
 */
public record VerificationEmailEvent(String email, String displayName, String locale, String verificationToken,
        String username) {
}
