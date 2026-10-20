package com.capstone.steam.auth;

import java.time.Clock;
import java.time.Duration;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Límite de correos de verificación (registro y reenvío): {@code perHour} por email y hora. La clave es el email
 * ya normalizado.
 */
@Component
public class ResendRateLimiter extends SlidingWindowLimiter {

    /** Tope de emails distintos en memoria. */
    static final int MAX_ENTRIES = 100_000;

    @Autowired
    public ResendRateLimiter(Clock clock, EmailVerificationProperties properties) {
        this(clock, properties.resendPerHour(), MAX_ENTRIES);
    }

    ResendRateLimiter(Clock clock, int perHour) {
        this(clock, perHour, MAX_ENTRIES);
    }

    ResendRateLimiter(Clock clock, int perHour, int maxEntries) {
        super(clock, perHour, Duration.ofHours(1), maxEntries, "auth.resendRateLimited");
    }

    /** Emails con intentos en el mapa (para los tests). */
    int trackedEmails() {
        return trackedKeys();
    }
}
