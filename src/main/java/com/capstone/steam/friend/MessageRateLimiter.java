package com.capstone.steam.friend;

import java.time.Clock;
import java.time.Duration;

import org.springframework.stereotype.Component;

import com.capstone.steam.auth.SlidingWindowLimiter;

/** Límite de envío del chat: {@value #MAX_PER_MINUTE} mensajes por usuario y minuto. La clave es el id del usuario. */
@Component
public class MessageRateLimiter extends SlidingWindowLimiter {

    static final int MAX_PER_MINUTE = 30;
    /** Tope de usuarios distintos en memoria. */
    static final int MAX_ENTRIES = 100_000;

    public MessageRateLimiter(Clock clock) {
        super(clock, MAX_PER_MINUTE, Duration.ofMinutes(1), MAX_ENTRIES, "chat.rateLimited", true);
    }
}
