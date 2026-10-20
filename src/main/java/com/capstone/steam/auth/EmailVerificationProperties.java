package com.capstone.steam.auth;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * @param required      si es {@code false}, registrarse crea la cuenta ya verificada y devuelve la sesión
 *                      (solo los tests lo desactivan); por defecto {@code true}
 * @param ttl           validez del enlace del correo (p. ej. P1D); también el tiempo que una cuenta sin verificar
 *                      retiene su usuario
 * @param resendPerHour máximo de correos de verificación por email y hora (registro y reenvío)
 */
@ConfigurationProperties("app.auth.email-verification")
public record EmailVerificationProperties(
        @DefaultValue("true") boolean required,
        @DefaultValue("P1D") Duration ttl,
        @DefaultValue("3") int resendPerHour) {
}
