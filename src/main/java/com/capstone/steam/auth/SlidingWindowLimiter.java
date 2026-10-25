package com.capstone.steam.auth;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.ConcurrentHashMap;

import com.capstone.steam.common.exception.RateLimitedException;

/**
 * Limite di {@code max} richieste per chiave all'interno di una finestra scorrevole, in memoria (si perde al riavvio
 * e non è condiviso tra istanze: basta per una). Le voci senza tentativi recenti vengono ripulite perché la
 * mappa non cresca senza fine, e con {@code maxEntries} chiavi distinte le chiavi nuove vengono rifiutate (429) invece
 * di scalzare quelle esistenti: così riempire la mappa non cancella il limite di un'altra chiave. Questo tetto è protetto da un
 * limite per IP davanti.
 */
public class SlidingWindowLimiter {

    private static final Duration SWEEP_EVERY = Duration.ofMinutes(5);

    private final ConcurrentHashMap<String, Deque<Instant>> hits = new ConcurrentHashMap<>();
    private final Clock clock;
    private final int max;
    private final Duration window;
    private final int maxEntries;
    private final String errorCode;
    private final boolean exactRetryAfter;
    private volatile Instant nextSweep;
    /** Ultima pulizia completa fatta perché la mappa era piena: al massimo una al secondo. */
    private volatile Instant lastFullSweep = Instant.MIN;
    private final java.util.concurrent.atomic.AtomicInteger fullSweeps = new java.util.concurrent.atomic.AtomicInteger();

    /**
     * @param errorCode       chiave del testo 429 in messages*.properties
     * @param exactRetryAfter {@code true}: Retry-After è il tempo reale fino alla scadenza del tentativo più vecchio;
     *                        {@code false}: sempre l'intera finestra (non rivela l'ora dell'ultima attività)
     */
    protected SlidingWindowLimiter(Clock clock, int max, Duration window, int maxEntries, String errorCode,
            boolean exactRetryAfter) {
        this.exactRetryAfter = exactRetryAfter;
        this.clock = clock;
        this.max = max;
        this.window = window;
        this.maxEntries = maxEntries;
        this.errorCode = errorCode;
        this.nextSweep = clock.instant().plus(SWEEP_EVERY);
    }

    /** Registra un tentativo della chiave; lancia 429 se ne ha già {@code max} nella finestra o se la mappa è piena. */
    public void check(String key) {
        Instant now = clock.instant();
        sweepIfDue(now);
        if (hits.size() >= maxEntries && !hits.containsKey(key)) {
            // pieno: una pulizia completa scorre tutta la mappa, quindi si fa al massimo una volta al secondo
            // (un attaccante non può forzarne una per richiesta)
            if (Duration.between(lastFullSweep, now).compareTo(Duration.ofSeconds(1)) >= 0) {
                lastFullSweep = now;
                fullSweeps.incrementAndGet();
                sweep(now);
            }
            if (hits.size() >= maxEntries) {
                throw new RateLimitedException(errorCode, window.toSeconds());
            }
        }
        boolean[] allowed = {false};
        long[] retryAfter = {window.toSeconds()};
        hits.compute(key, (k, queue) -> {
            Deque<Instant> q = queue == null ? new ArrayDeque<>() : queue;
            evictOld(q, now);
            if (q.size() < max) {
                q.addLast(now);
                allowed[0] = true;
            } else {
                // quando scade il tentativo più vecchio della finestra c'è di nuovo posto
                if (exactRetryAfter && !q.isEmpty()) { // con max = 0 la coda è vuota: resta l'intera finestra
                    retryAfter[0] = Duration.between(now, q.peekFirst().plus(window)).toSeconds() + 1;
                }
            }
            return q.isEmpty() ? null : q;
        });
        if (!allowed[0]) {
            throw new RateLimitedException(errorCode, retryAfter[0]);
        }
    }

    void clear() {
        hits.clear();
    }

    int fullSweeps() {
        return fullSweeps.get();
    }

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
