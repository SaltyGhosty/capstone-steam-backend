package com.capstone.steam.user;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * @param deletionGrace periodo tra la richiesta di cancellazione e l'anonimizzazione dell'account (es. P14D)
 * @param deletionCron  quando gira il task che anonimizza gli account il cui periodo è scaduto
 */
@ConfigurationProperties("app.account")
public record AccountDeletionProperties(
        @DefaultValue("P14D") Duration deletionGrace,
        @DefaultValue("0 15 4 * * *") String deletionCron) {
}
