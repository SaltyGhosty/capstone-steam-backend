package com.capstone.steam.user;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * @param deletionGrace plazo entre pedir la baja y anonimizar la cuenta (p. ej. P14D)
 * @param deletionCron  cuándo se ejecuta la tarea que anonimiza las cuentas cuyo plazo ha vencido
 */
@ConfigurationProperties("app.account")
public record AccountDeletionProperties(
        @DefaultValue("P14D") Duration deletionGrace,
        @DefaultValue("0 15 4 * * *") String deletionCron) {
}
