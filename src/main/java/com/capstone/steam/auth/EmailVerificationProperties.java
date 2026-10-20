package com.capstone.steam.auth;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @param required      si es {@code false}, registrarse crea la cuenta ya verificada y devuelve la sesión
 *                      (solo los tests lo desactivan); por defecto {@code true}
 * @param ttl           validez del enlace del correo (p. ej. P1D)
 * @param resendPerHour máximo de reenvíos por email y hora
 */
@ConfigurationProperties("app.auth.email-verification")
public record EmailVerificationProperties(boolean required, Duration ttl, int resendPerHour) {
}
