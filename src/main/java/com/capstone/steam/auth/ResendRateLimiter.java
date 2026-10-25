package com.capstone.steam.auth;

import java.time.Clock;
import java.time.Duration;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Limite di email di verifica (registrazione e reinvio): {@code perHour} per indirizzo e ora. La chiave è l'email
 * già normalizzata.
 */
@Component
public class ResendRateLimiter extends SlidingWindowLimiter {

    /** Massimo di email distinte in memoria. */
    static final int MAX_ENTRIES = 100_000;

    @Autowired
    public ResendRateLimiter(Clock clock, EmailVerificationProperties properties) {
        this(clock, properties.resendPerHour(), MAX_ENTRIES);
    }

    ResendRateLimiter(Clock clock, int perHour) {
        this(clock, perHour, MAX_ENTRIES);
    }

    ResendRateLimiter(Clock clock, int perHour, int maxEntries) {
        // Retry-After costante (l'intera finestra): il valore esatto rivelerebbe l'ora dell'ultima attività dell'email
        super(clock, perHour, Duration.ofHours(1), maxEntries, "auth.resendRateLimited", false);
    }

    int trackedEmails() {
        return trackedKeys();
    }
}
