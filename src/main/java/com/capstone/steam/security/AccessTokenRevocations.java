package com.capstone.steam.security;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Marcas de revocación de access tokens por usuario (D16). Al desactivar una cuenta o cambiar sus roles
 * se guarda el instante; todo token emitido en ese segundo o antes se rechaza, sin consultar la BD.
 * Las marcas se olvidan pasada la vida del access token (ya no puede quedar ningún token anterior válido).
 * Está en memoria: se pierde al reiniciar y no se comparte entre instancias.
 */
@Component
public class AccessTokenRevocations {

    private final Map<Long, Instant> revokedAt = new ConcurrentHashMap<>();
    private final Clock clock;
    private final Duration tokenLifetime;

    @Autowired
    public AccessTokenRevocations(Clock clock, JwtProperties properties) {
        this(clock, properties.expiration());
    }

    AccessTokenRevocations(Clock clock, Duration tokenLifetime) {
        this.clock = clock;
        this.tokenLifetime = tokenLifetime;
    }

    public void revokeUser(Long userId) {
        purgeExpired();
        revokedAt.put(userId, clock.instant());
    }

    /** true si hay marca del usuario y el token se emitió en el mismo segundo de la marca o antes (el iat tiene precisión de segundos). */
    public boolean isRevoked(Long userId, Instant issuedAt) {
        Instant mark = revokedAt.get(userId);
        if (mark == null) {
            return false;
        }
        if (isExpired(mark)) {
            revokedAt.remove(userId, mark);
            return false;
        }
        return issuedAt == null || issuedAt.getEpochSecond() <= mark.getEpochSecond();
    }

    int size() {
        return revokedAt.size();
    }

    private void purgeExpired() {
        revokedAt.values().removeIf(this::isExpired);
    }

    private boolean isExpired(Instant mark) {
        return !mark.plus(tokenLifetime).isAfter(clock.instant());
    }
}
