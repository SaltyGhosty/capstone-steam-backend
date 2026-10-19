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
    /** Mensajes de todos los usuarios en la última hora (para el tope global). */
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

    /** OpenRouter dijo 429: durante el enfriamiento no se llama al modelo. */
    public void startCooldown() {
        cooldownUntil = clock.instant().plus(cooldown);
    }

    /**
     * Anota un mensaje del usuario. Lanza 503 durante el enfriamiento o si el tope global de la hora
     * está lleno, y 429 si el usuario ya tiene {@value #MAX_PER_WINDOW} en la última hora.
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
