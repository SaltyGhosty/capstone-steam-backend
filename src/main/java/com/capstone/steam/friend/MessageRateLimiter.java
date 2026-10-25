package com.capstone.steam.friend;

import java.time.Clock;
import java.time.Duration;

import org.springframework.stereotype.Component;

import com.capstone.steam.auth.SlidingWindowLimiter;

/** Limite di invio della chat: {@value #MAX_PER_MINUTE} messaggi per utente al minuto. La chiave è l'id dell'utente. */
@Component
public class MessageRateLimiter extends SlidingWindowLimiter {

    static final int MAX_PER_MINUTE = 30;
    /** Tetto di utenti distinti in memoria. */
    static final int MAX_ENTRIES = 100_000;

    public MessageRateLimiter(Clock clock) {
        super(clock, MAX_PER_MINUTE, Duration.ofMinutes(1), MAX_ENTRIES, "chat.rateLimited", true);
    }
}
