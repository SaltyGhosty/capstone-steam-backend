package com.capstone.steam.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AccessTokenRevocationsTest {

    private static final Instant T0 = Instant.parse("2026-01-01T10:00:00.500Z");
    private static final Duration LIFETIME = Duration.ofMinutes(15);

    private static final class MutableClock extends Clock {
        Instant now = T0;
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
    }

    private MutableClock clock;
    private AccessTokenRevocations revocations;

    @BeforeEach
    void setUp() {
        clock = new MutableClock();
        revocations = new AccessTokenRevocations(clock, LIFETIME);
    }

    @Test
    void userWithoutMarkIsNotRevoked() {
        assertThat(revocations.isRevoked(1L, T0.minusSeconds(60))).isFalse();
    }

    @Test
    void tokenIssuedBeforeMarkIsRevoked() {
        revocations.revokeUser(1L);
        assertThat(revocations.isRevoked(1L, T0.minusSeconds(60))).isTrue();
    }

    @Test
    void tokenIssuedInTheSameSecondIsRevoked() {
        revocations.revokeUser(1L); // marca 10:00:00.500; el iat llega truncado a 10:00:00
        assertThat(revocations.isRevoked(1L, Instant.parse("2026-01-01T10:00:00Z"))).isTrue();
    }

    @Test
    void tokenIssuedAfterMarkIsValid() {
        revocations.revokeUser(1L);
        assertThat(revocations.isRevoked(1L, Instant.parse("2026-01-01T10:00:01Z"))).isFalse();
    }

    @Test
    void otherUsersAreNotAffected() {
        revocations.revokeUser(1L);
        assertThat(revocations.isRevoked(2L, T0.minusSeconds(60))).isFalse();
    }

    @Test
    void markIsForgottenAfterTokenLifetime() {
        revocations.revokeUser(1L);
        clock.now = T0.plus(LIFETIME);
        assertThat(revocations.isRevoked(1L, T0.minusSeconds(60))).isFalse();
        assertThat(revocations.size()).isZero();
    }

    @Test
    void expiredMarksArePurgedOnNextRevocation() {
        revocations.revokeUser(1L);
        clock.now = T0.plus(LIFETIME).plusSeconds(1);
        revocations.revokeUser(2L);
        assertThat(revocations.size()).isEqualTo(1);
    }
}
