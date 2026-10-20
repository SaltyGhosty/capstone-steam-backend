package com.capstone.steam.auth;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.capstone.steam.common.exception.TooManyRequestsException;

/**
 * Límite de reenvíos del correo de verificación: {@code perHour} por email y hora, con ventana
 * deslizante en memoria (se pierde al reiniciar y no se comparte entre instancias: basta para una).
 * La clave es el email ya normalizado. Las entradas sin intentos recientes se barren para que el mapa no
 * crezca sin fin.
 */
@Component
public class ResendRateLimiter {

    static final Duration WINDOW = Duration.ofHours(1);
    private static final Duration SWEEP_EVERY = Duration.ofMinutes(5);
    /** Tope de emails distintos en memoria: un atacante con millones de emails no puede llenar el heap. */
    static final int MAX_ENTRIES = 10_000;

    private final ConcurrentHashMap<String, Deque<Instant>> hits = new ConcurrentHashMap<>();
    private final Clock clock;
    private final int perHour;
    private final int maxEntries;
    private volatile Instant nextSweep;

    @Autowired
    public ResendRateLimiter(Clock clock, EmailVerificationProperties properties) {
        this(clock, properties.resendPerHour());
    }

    ResendRateLimiter(Clock clock, int perHour) {
        this(clock, perHour, MAX_ENTRIES);
    }

    ResendRateLimiter(Clock clock, int perHour, int maxEntries) {
        this.clock = clock;
        this.perHour = perHour;
        this.maxEntries = maxEntries;
        this.nextSweep = clock.instant().plus(SWEEP_EVERY);
    }

    /** Anota un reenvío; lanza 429 si el email ya tiene {@code perHour} en la última hora. */
    public void check(String emailKey) {
        Instant now = clock.instant();
        sweepIfDue(now);
        if (hits.size() >= maxEntries && !hits.containsKey(emailKey)) {
            // lleno: tras barrer lo caducado, si sigue lleno se rechazan las claves nuevas (nunca se sacan las
            // existentes, o un atacante con muchos emails podría borrar el límite de su víctima)
            sweep(now);
            if (hits.size() >= maxEntries) {
                throw new TooManyRequestsException("auth.resendRateLimited");
            }
        }
        boolean[] allowed = {false};
        hits.compute(emailKey, (key, queue) -> {
            Deque<Instant> q = queue == null ? new ArrayDeque<>() : queue;
            evictOld(q, now);
            if (q.size() < perHour) {
                q.addLast(now);
                allowed[0] = true;
            }
            return q.isEmpty() ? null : q;
        });
        if (!allowed[0]) {
            throw new TooManyRequestsException("auth.resendRateLimited");
        }
    }

    /** Vacía el estado (para los tests). */
    void clear() {
        hits.clear();
    }

    /** Emails con intentos en el mapa (para los tests). */
    int trackedEmails() {
        return hits.size();
    }

    private void sweepIfDue(Instant now) {
        if (now.isBefore(nextSweep)) {
            return;
        }
        nextSweep = now.plus(SWEEP_EVERY);
        sweep(now);
    }

    private void sweep(Instant now) {
        hits.keySet().forEach(key -> hits.computeIfPresent(key, (k, q) -> {
            evictOld(q, now);
            return q.isEmpty() ? null : q;
        }));
    }

    private static void evictOld(Deque<Instant> queue, Instant now) {
        Instant limit = now.minus(WINDOW);
        while (!queue.isEmpty() && !queue.peekFirst().isAfter(limit)) {
            queue.pollFirst();
        }
    }
}
