package com.capstone.steam.support;

import java.time.Clock;
import java.time.Duration;

import org.springframework.stereotype.Component;

import com.capstone.steam.auth.SlidingWindowLimiter;

/** Limite del form di assistenza: 5 messaggi all'ora per email (la chiave è già normalizzata). */
@Component
public class SupportRateLimiter extends SlidingWindowLimiter {

    static final int PER_HOUR = 5;
    static final int MAX_ENTRIES = 100_000;

    public SupportRateLimiter(Clock clock) {
        // Retry-After costante (l'intera finestra): non rivela quando quell'email ha scritto l'ultima volta
        super(clock, PER_HOUR, Duration.ofHours(1), MAX_ENTRIES, "support.rateLimited", false);
    }
}
