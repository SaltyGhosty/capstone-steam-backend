package com.capstone.steam.security;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Marche di revoca degli access token per utente (D16). Quando si disattiva un account o ne cambiano i ruoli
 * si salva l'istante; ogni token emesso in quel secondo o prima viene rifiutato, senza interrogare il DB.
 * Le marche si dimenticano passata la vita dell'access token (non può più restare nessun token precedente valido).
 * È in memoria: si perde al riavvio e non è condivisa tra istanze.
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

    /** true se esiste una marca dell'utente e il token è stato emesso nello stesso secondo della marca o prima (l'iat ha precisione di secondi). */
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
