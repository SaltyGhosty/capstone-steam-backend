package com.capstone.steam.security;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @param secret            chiave HMAC in Base64 (minimo 32 byte decodificati)
 * @param expiration        validità dell'access token (es. PT15M)
 * @param refreshExpiration validità del refresh token (es. P7D)
 */
@ConfigurationProperties("app.jwt")
public record JwtProperties(String secret, Duration expiration, Duration refreshExpiration) {
}
