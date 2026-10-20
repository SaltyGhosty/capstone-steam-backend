package com.capstone.steam.auth;

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

class ResendRateLimiterTest {

    /** Reloj que se avanza a mano. */
    private static final class MutableClock extends Clock {
        private Instant now = Instant.parse("2026-10-20T10:00:00Z");

        void advance(Duration d) {
            now = now.plus(d);
        }

        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
    }

    private MutableClock clock;
    private ResendRateLimiter limiter;

    @BeforeEach
    void setUp() {
        clock = new MutableClock();
        limiter = new ResendRateLimiter(clock, 3);
    }

    @Test
    void allowsThreePerHourAndRejectsTheFourth() {
        for (int i = 0; i < 3; i++) {
            limiter.check("ana@test.com");
            clock.advance(Duration.ofMinutes(1));
        }

        assertThatThrownBy(() -> limiter.check("ana@test.com"))
                .isInstanceOf(TooManyRequestsException.class)
                .hasMessageContaining("demasiados");
    }

    @Test
    void anAttemptRejectedByTheLimitDoesNotExtendTheBlock() {
        for (int i = 0; i < 3; i++) {
            limiter.check("ana@test.com");
        }
        clock.advance(Duration.ofMinutes(30));
        assertThatThrownBy(() -> limiter.check("ana@test.com")).isInstanceOf(TooManyRequestsException.class);

        clock.advance(Duration.ofMinutes(30).plusSeconds(1)); // la hora de los tres primeros ya pasó
        assertThatCode(() -> limiter.check("ana@test.com")).doesNotThrowAnyException();
    }

    @Test
    void passesAgainAfterOneHour() {
        for (int i = 0; i < 3; i++) {
            limiter.check("ana@test.com");
        }
        clock.advance(Duration.ofHours(1));

        assertThatCode(() -> limiter.check("ana@test.com")).doesNotThrowAnyException();
    }

    @Test
    void differentEmailsDoNotAffectEachOther() {
        for (int i = 0; i < 3; i++) {
            limiter.check("ana@test.com");
        }

        assertThatCode(() -> limiter.check("leo@test.com")).doesNotThrowAnyException();
    }

    @Test
    void theMapNeverGrowsPastItsCapAndDropsTheOldestEntries() {
        limiter = new ResendRateLimiter(clock, 3, 10);
        for (int i = 0; i < 50; i++) {
            limiter.check("victima" + i + "@test.com");
            clock.advance(Duration.ofSeconds(1));
            assertThat(limiter.trackedEmails()).isLessThanOrEqualTo(10);
        }
        // los últimos siguen contando; los más antiguos se descartaron (y vuelven a tener todo su cupo)
        for (int i = 0; i < 3; i++) {
            limiter.check("victima0@test.com");
        }
        assertThatThrownBy(() -> {
            for (int i = 0; i < 3; i++) {
                limiter.check("victima49@test.com");
            }
        }).isInstanceOf(TooManyRequestsException.class);
    }

    @Test
    void idleEntriesAreSweptSoTheMapDoesNotGrowForever() {
        limiter.check("ana@test.com");
        limiter.check("leo@test.com");
        assertThat(limiter.trackedEmails()).isEqualTo(2);

        clock.advance(Duration.ofHours(2));
        limiter.check("sam@test.com"); // el barrido corre al llamar

        assertThat(limiter.trackedEmails()).isEqualTo(1);
    }
}
