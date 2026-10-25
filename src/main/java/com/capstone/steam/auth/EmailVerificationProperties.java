package com.capstone.steam.auth;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * @param required      se è {@code false}, registrarsi crea l'account già verificato e restituisce la sessione
 *                      (lo disattivano solo i test); di default {@code true}
 * @param ttl           validità del link dell'email (es. P1D); è anche il tempo in cui un account non verificato
 *                      trattiene il suo username
 * @param resendPerHour massimo di email di verifica per indirizzo e ora (registrazione e reinvio)
 */
@ConfigurationProperties("app.auth.email-verification")
public record EmailVerificationProperties(
        @DefaultValue("true") boolean required,
        @DefaultValue("P1D") Duration ttl,
        @DefaultValue("3") int resendPerHour) {
}
