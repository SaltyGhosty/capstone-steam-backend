package com.capstone.steam.support;

import java.time.Clock;
import java.time.Duration;

import org.springframework.stereotype.Component;

import com.capstone.steam.auth.SlidingWindowLimiter;

/** Límite del formulario de asistencia: 5 mensajes por hora y email (la clave ya va normalizada). */
@Component
public class SupportRateLimiter extends SlidingWindowLimiter {

    static final int PER_HOUR = 5;
    static final int MAX_ENTRIES = 100_000;

    public SupportRateLimiter(Clock clock) {
        // Retry-After constante (la ventana entera): no delata cuándo escribió la última vez ese email
        super(clock, PER_HOUR, Duration.ofHours(1), MAX_ENTRIES, "support.rateLimited", false);
    }
}
