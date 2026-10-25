package com.capstone.steam.recommendation;

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
 * Limite per utente: {@value #MAX_PER_WINDOW} messaggi all'ora con finestra scorrevole, in memoria
 * (si perde al riavvio e non è condiviso tra istanze: basta per una sola istanza).
 * Le voci senza messaggi recenti vengono cancellate perché la mappa non cresca all'infinito.
 */
@Component
public class ChatRateLimiter {

    static final int MAX_PER_WINDOW = 20;
    static final Duration WINDOW = Duration.ofHours(1);
    /** Ogni quanto si ripuliscono gli utenti che non sono tornati. */
    private static final Duration SWEEP_EVERY = Duration.ofMinutes(5);

    private final ConcurrentHashMap<Long, Deque<Instant>> hits = new ConcurrentHashMap<>();
    /** Messaggi di tutti gli utenti nell'ultima ora (per il tetto globale). */
    private final Deque<Instant> global = new ArrayDeque<>();
    private final Clock clock;
    private final int globalPerHour;
    private final Duration cooldown;
    private volatile Instant nextSweep;
    private volatile Instant cooldownUntil = Instant.MIN;

    @Autowired
    public ChatRateLimiter(OpenRouterProperties properties) {
        this(Clock.systemUTC(), properties.globalPerHour(), properties.cooldown());
    }

    ChatRateLimiter(Clock clock) {
        this(clock, OpenRouterProperties.DEFAULT_GLOBAL_PER_HOUR, OpenRouterProperties.DEFAULT_COOLDOWN);
    }

    ChatRateLimiter(Clock clock, int globalPerHour, Duration cooldown) {
        this.clock = clock;
        this.globalPerHour = globalPerHour;
        this.cooldown = cooldown;
        this.nextSweep = clock.instant().plus(SWEEP_EVERY);
    }

    /** OpenRouter ha detto 429: durante il raffreddamento non si chiama il modello. */
    public void startCooldown() {
        cooldownUntil = clock.instant().plus(cooldown);
    }

    /**
     * Registra un messaggio dell'utente. Lancia 503 durante il raffreddamento o se il tetto globale dell'ora
     * è pieno, e 429 se l'utente ne ha già {@value #MAX_PER_WINDOW} nell'ultima ora.
     */
    public void acquire(long userId) {
        Instant now = clock.instant();
        if (now.isBefore(cooldownUntil)) {
            throw new AssistantUnavailableException();
        }
        sweepIfDue(now);
        synchronized (global) {
            evictOld(global, now);
            if (global.size() >= globalPerHour) {
                throw new AssistantUnavailableException();
            }
        }
        boolean[] allowed = {false};
        hits.compute(userId, (id, queue) -> {
            Deque<Instant> q = queue == null ? new ArrayDeque<>() : queue;
            evictOld(q, now);
            if (q.size() < MAX_PER_WINDOW) {
                q.addLast(now);
                allowed[0] = true;
            }
            return q.isEmpty() ? null : q;
        });
        if (!allowed[0]) {
            throw new TooManyRequestsException("assistant.rateLimited");
        }
        synchronized (global) {
            global.addLast(now);
        }
    }

    /** Utenti con messaggi nella mappa (per i test). */
    int trackedUsers() {
        return hits.size();
    }

    private void sweepIfDue(Instant now) {
        if (now.isBefore(nextSweep)) {
            return;
        }
        nextSweep = now.plus(SWEEP_EVERY);
        hits.keySet().forEach(id -> hits.computeIfPresent(id, (k, q) -> {
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
