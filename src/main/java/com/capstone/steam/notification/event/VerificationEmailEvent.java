package com.capstone.steam.notification.event;

/**
 * Reenvío del enlace de verificación. Lleva el token en claro hasta el listener de correo
 * (AFTER_COMMIT); nunca se guarda ni se escribe en el log con el correo activo.
 *
 * @param username la cuenta que se va a confirmar: el correo la nombra para que quien no la pidió la reconozca
 */
public record VerificationEmailEvent(String email, String displayName, String locale, String verificationToken,
        String username) {
}
