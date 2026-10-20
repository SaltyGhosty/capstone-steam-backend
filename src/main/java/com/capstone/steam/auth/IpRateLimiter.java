package com.capstone.steam.auth;

import java.time.Clock;
import java.time.Duration;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Límite por IP delante de registro, reenvío, verificación y vista previa: {@code perMinute} peticiones por minuto
 * y dirección (por defecto 20, {@code app.auth.ip-limit.per-minute}). Frena a quien intente llenar el mapa del
 * límite por email o adivinar enlaces desde una sola máquina. Detrás del proxy de Render la IP real la deja
 * {@code server.forward-headers-strategy: native} (en prod).
 */
@Component
public class IpRateLimiter extends SlidingWindowLimiter {

    static final int MAX_ENTRIES = 100_000;

    @Autowired
    public IpRateLimiter(Clock clock, IpLimitProperties properties) {
        this(clock, properties.perMinute());
    }

    IpRateLimiter(Clock clock, int perMinute) {
        super(clock, perMinute, Duration.ofMinutes(1), MAX_ENTRIES, "auth.tooManyRequests");
    }
}
