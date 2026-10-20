package com.capstone.steam.auth;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.ConcurrentHashMap;

import com.capstone.steam.common.exception.TooManyRequestsException;

/**
 * Límite de {@code max} peticiones por clave dentro de una ventana deslizante, en memoria (se pierde al reiniciar
 * y no se comparte entre instancias: basta para una). Las entradas sin intentos recientes se barren para que el
 * mapa no crezca sin fin, y con {@code maxEntries} claves distintas se rechazan las claves nuevas (429) en vez
 * de sacar las existentes: así llenar el mapa no borra el límite de otra clave. Ese tope se protege con un
 * límite por IP delante.
 */
public class SlidingWindowLimiter {

    private static final Duration SWEEP_EVERY = Duration.ofMinutes(5);

    private final ConcurrentHashMap<String, Deque<Instant>> hits = new ConcurrentHashMap<>();
    private final Clock clock;
    private final int max;
    private final Duration window;
    private final int maxEntries;
    private final String errorCode;
    private volatile Instant nextSweep;

    /** @param errorCode clave del texto 429 en messages*.properties */
    protected SlidingWindowLimiter(Clock clock, int max, Duration window, int maxEntries, String errorCode) {
        this.clock = clock;
        this.max = max;
        this.window = window;
        this.maxEntries = maxEntries;
        this.errorCode = errorCode;
        this.nextSweep = clock.instant().plus(SWEEP_EVERY);
    }

    /** Anota un intento de la clave; lanza 429 si ya tiene {@code max} en la ventana o si el mapa está lleno. */
    public void check(String key) {
        Instant now = clock.instant();
        sweepIfDue(now);
        if (hits.size() >= maxEntries && !hits.containsKey(key)) {
            sweep(now);
            if (hits.size() >= maxEntries) {
                throw new TooManyRequestsException(errorCode);
            }
        }
        boolean[] allowed = {false};
        hits.compute(key, (k, queue) -> {
            Deque<Instant> q = queue == null ? new ArrayDeque<>() : queue;
            evictOld(q, now);
            if (q.size() < max) {
                q.addLast(now);
                allowed[0] = true;
            }
            return q.isEmpty() ? null : q;
        });
        if (!allowed[0]) {
            throw new TooManyRequestsException(errorCode);
        }
    }

    /** Vacía el estado (para los tests). */
    void clear() {
        hits.clear();
    }

    /** Claves con intentos en el mapa (para los tests). */
    int trackedKeys() {
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

    private void evictOld(Deque<Instant> queue, Instant now) {
        Instant limit = now.minus(window);
        while (!queue.isEmpty() && !queue.peekFirst().isAfter(limit)) {
            queue.pollFirst();
        }
    }
}
