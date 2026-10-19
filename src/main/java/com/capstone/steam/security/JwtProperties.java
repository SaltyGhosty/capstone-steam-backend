package com.capstone.steam.security;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @param secret            clave HMAC en Base64 (mínimo 32 bytes decodificados)
 * @param expiration        validez del access token (p. ej. PT15M)
 * @param refreshExpiration validez del refresh token (p. ej. P7D)
 */
@ConfigurationProperties("app.jwt")
public record JwtProperties(String secret, Duration expiration, Duration refreshExpiration) {
}
