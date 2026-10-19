package com.capstone.steam.recommendation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.capstone.steam.common.exception.TooManyRequestsException;

class ChatRateLimiterTest {

    /** Reloj que se avanza a mano. */
    private static final class MutableClock extends Clock {
        private Instant now = Instant.parse("2026-10-19T10:00:00Z");

        void advance(Duration d) {
            now = now.plus(d);
        }

        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
    }

    private MutableClock clock;
    private ChatRateLimiter limiter;

    @BeforeEach
    void setUp() {
        clock = new MutableClock();
        limiter = new ChatRateLimiter(clock);
    }

    @Test
    void allowsTwentyPerHourAndRejectsTheTwentyFirst() {
        for (int i = 0; i < 20; i++) {
            limiter.acquire(1L);
            clock.advance(Duration.ofMinutes(1)); // los 20 caben en 20 minutos
        }

        assertThatThrownBy(() -> limiter.acquire(1L)).isInstanceOf(TooManyRequestsException.class);
    }

    @Test
    void allowsAgainWhenTheOldestMessagesLeaveTheWindow() {
        for (int i = 0; i < 20; i++) {
            limiter.acquire(1L);
        }
        assertThatThrownBy(() -> limiter.acquire(1L)).isInstanceOf(TooManyRequestsException.class);

        clock.advance(Duration.ofHours(1));

        assertThatCode(() -> limiter.acquire(1L)).doesNotThrowAnyException();
    }

    @Test
    void usersDoNotAffectEachOther() {
        for (int i = 0; i < 20; i++) {
            limiter.acquire(1L);
        }

        assertThatCode(() -> limiter.acquire(2L)).doesNotThrowAnyException();
    }

    @Test
    void theGlobalCapPerHourIs503ForEveryoneAndRecoversAfterTheWindow() {
        limiter = new ChatRateLimiter(clock, 3, Duration.ofMinutes(5));
        limiter.acquire(1L);
        limiter.acquire(2L);
        limiter.acquire(3L);

        assertThatThrownBy(() -> limiter.acquire(4L)).isInstanceOf(AssistantUnavailableException.class);
        assertThatThrownBy(() -> limiter.acquire(1L)).isInstanceOf(AssistantUnavailableException.class);

        clock.advance(Duration.ofHours(1));
        assertThatCode(() -> limiter.acquire(4L)).doesNotThrowAnyException();
    }

    @Test
    void aRejectedGlobalRequestDoesNotUseUpTheUserQuota() {
        limiter = new ChatRateLimiter(clock, 1, Duration.ofMinutes(5));
        limiter.acquire(1L);
        assertThatThrownBy(() -> limiter.acquire(2L)).isInstanceOf(AssistantUnavailableException.class);

        clock.advance(Duration.ofHours(1));
        limiter.acquire(2L);
        assertThat(limiter.trackedUsers()).isEqualTo(1);
    }

    @Test
    void afterACooldownStartsEveryoneGets503UntilItEnds() {
        limiter.startCooldown();

        assertThatThrownBy(() -> limiter.acquire(1L)).isInstanceOf(AssistantUnavailableException.class);
        clock.advance(Duration.ofMinutes(4));
        assertThatThrownBy(() -> limiter.acquire(2L)).isInstanceOf(AssistantUnavailableException.class);
        clock.advance(Duration.ofMinutes(1));
        assertThatCode(() -> limiter.acquire(1L)).doesNotThrowAnyException();
    }

    @Test
    void emptyEntriesAreRemovedSoTheMapDoesNotGrowForever() {
        for (long user = 1; user <= 50; user++) {
            limiter.acquire(user);
        }
        assertThat(limiter.trackedUsers()).isEqualTo(50);

        clock.advance(Duration.ofHours(2));
        limiter.acquire(99L); // una llamada posterior barre a los usuarios que ya no cuentan

        assertThat(limiter.trackedUsers()).isEqualTo(1);
    }
}
