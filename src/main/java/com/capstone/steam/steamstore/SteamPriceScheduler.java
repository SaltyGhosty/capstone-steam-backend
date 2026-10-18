package com.capstone.steam.steamstore;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Sincronización automática: al arrancar (en Render el servidor se duerme y se despierta a menudo)
 * y luego según {@code app.steam.sync-cron}. Un fallo de Steam solo se registra en el log.
 */
@Component
@ConditionalOnProperty(prefix = "app.steam", name = "sync-enabled", havingValue = "true")
class SteamPriceScheduler {

    private static final Logger log = LoggerFactory.getLogger(SteamPriceScheduler.class);

    private final SteamPriceSyncService syncService;

    SteamPriceScheduler(SteamPriceSyncService syncService) {
        this.syncService = syncService;
    }

    @EventListener(ApplicationReadyEvent.class)
    void onStartup() {
        run();
    }

    @Scheduled(cron = "${app.steam.sync-cron}")
    void scheduled() {
        run();
    }

    private void run() {
        try {
            syncService.sync();
        } catch (RuntimeException e) {
            log.warn("No se pudieron sincronizar los precios con Steam: {}", e.getMessage());
        }
    }
}
