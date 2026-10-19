package com.capstone.steam.auth;

import java.time.Duration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Limpia {@code refresh_tokens} según {@code app.auth.refresh-purge-cron}. Solo borra familias enteras
 * caducadas hace más de {@code app.auth.refresh-purge-grace}. Un fallo solo se registra en el log.
 */
@Component
class RefreshTokenPurgeScheduler {

    private static final Logger log = LoggerFactory.getLogger(RefreshTokenPurgeScheduler.class);

    private final RefreshTokenService service;
    private final Duration grace;

    RefreshTokenPurgeScheduler(RefreshTokenService service,
            @Value("${app.auth.refresh-purge-grace}") Duration grace) {
        this.service = service;
        this.grace = grace;
    }

    @Scheduled(cron = "${app.auth.refresh-purge-cron}")
    void purge() {
        try {
            log.info("Purga de refresh tokens: {} filas borradas", service.purgeExpired(grace));
        } catch (RuntimeException e) {
            log.warn("No se pudo purgar refresh_tokens: {}", e.getClass().getSimpleName());
        }
    }
}
