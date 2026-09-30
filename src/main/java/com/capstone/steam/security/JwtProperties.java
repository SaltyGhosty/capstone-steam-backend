package com.capstone.steam.security;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @param secret     clave HMAC en Base64 (mínimo 32 bytes decodificados)
 * @param expiration validez del access token (p. ej. PT2H)
 */
@ConfigurationProperties("app.jwt")
public record JwtProperties(String secret, Duration expiration) {
}
