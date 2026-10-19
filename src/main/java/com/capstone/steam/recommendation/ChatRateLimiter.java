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
 * Límite por usuario: {@value #MAX_PER_WINDOW} mensajes por hora con ventana deslizante, en memoria
 * (se pierde al reiniciar, y no se comparte entre instancias: basta para una sola instancia).
 * Las entradas sin mensajes recientes se borran para que el mapa no crezca sin fin.
 */
@Component
public class ChatRateLimiter {

    static final int MAX_PER_WINDOW = 20;
    static final Duration WINDOW = Duration.ofHours(1);
    /** Cada cuánto se barren los usuarios que ya no han vuelto. */
    private static final Duration SWEEP_EVERY = Duration.ofMinutes(5);

    private final ConcurrentHashMap<Long, Deque<Instant>> hits = new ConcurrentHashMap<>();
    private final Clock clock;
    private volatile Instant nextSweep;

    @Autowired
    public ChatRateLimiter() {
        this(Clock.systemUTC());
    }

    ChatRateLimiter(Clock clock) {
        this.clock = clock;
        this.nextSweep = clock.instant().plus(SWEEP_EVERY);
    }

    /** Anota un mensaje del usuario. Si ya hay {@value #MAX_PER_WINDOW} en la última hora, lanza 429. */
    public void acquire(long userId) {
        Instant now = clock.instant();
        sweepIfDue(now);
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
    }

    /** Usuarios con mensajes en el mapa (para los tests). */
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
