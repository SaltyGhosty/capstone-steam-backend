package com.capstone.steam.auth;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.Min;

/** @param perMinute peticiones por minuto y IP a los endpoints públicos de cuenta (registro, reenvío, verificación) */
@ConfigurationProperties("app.auth.ip-limit")
@Validated
public record IpLimitProperties(@DefaultValue("60") @Min(1) int perMinute) {
}
