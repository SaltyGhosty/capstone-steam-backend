package com.capstone.steam.auth;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.Min;

/** @param perMinute richieste al minuto per IP agli endpoint pubblici dell'account (registrazione, reinvio, verifica) */
@ConfigurationProperties("app.auth.ip-limit")
@Validated
public record IpLimitProperties(@DefaultValue("60") @Min(1) int perMinute) {
}
