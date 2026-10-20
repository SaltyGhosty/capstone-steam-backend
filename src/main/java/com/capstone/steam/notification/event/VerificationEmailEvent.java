package com.capstone.steam.notification.event;

/**
 * Reenvío del enlace de verificación. Lleva el token en claro hasta el listener de correo
 * (AFTER_COMMIT); nunca se guarda ni se escribe en el log con el correo activo.
 */
public record VerificationEmailEvent(String email, String displayName, String locale, String verificationToken) {
}
