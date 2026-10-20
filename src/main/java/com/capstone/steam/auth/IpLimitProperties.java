package com.capstone.steam.auth;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/** @param perMinute peticiones por minuto y IP a los endpoints públicos de cuenta (registro, reenvío, verificación) */
@ConfigurationProperties("app.auth.ip-limit")
public record IpLimitProperties(@DefaultValue("20") int perMinute) {
}
