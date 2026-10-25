package com.capstone.steam.steamstore;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Connessione con l'API pubblica dello store di Steam (store.steampowered.com/api, senza chiave).
 *
 * @param syncEnabled sincronizza i prezzi da solo (all'avvio e secondo {@code syncCron}); il pulsante dell'admin funziona sempre
 * @param syncCron    ogni quanto si sincronizzano i prezzi
 * @param country     paese dello store, che fissa la valuta (es → euro)
 * @param baseUrl     radice dell'API (si cambia nei test)
 * @param timeout     tempo massimo di ogni richiesta a Steam
 */
@ConfigurationProperties("app.steam")
public record SteamProperties(boolean syncEnabled, String syncCron, String country, String baseUrl, Duration timeout) {
}
