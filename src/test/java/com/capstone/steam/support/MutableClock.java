package com.capstone.steam.support;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/**
 * Reloj de tests que se puede mover a mano. Se usa con {@code @Import(MutableClock.Config.class)} y se inyecta
 * con {@code @Autowired MutableClock}.
 */
public final class MutableClock extends Clock {

    public volatile Instant now = Instant.now();

    @Override public ZoneId getZone() { return ZoneOffset.UTC; }
    @Override public Clock withZone(ZoneId zone) { return this; }
    @Override public Instant instant() { return now; }

    @TestConfiguration
    public static class Config {
        @Bean
        @Primary
        MutableClock supportMutableClock() {
            return new MutableClock();
        }
    }
}
