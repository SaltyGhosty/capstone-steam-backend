package com.capstone.steam.steamstore;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Conexión con la API pública de la tienda de Steam (store.steampowered.com/api, sin clave).
 *
 * @param syncEnabled sincroniza precios solo (al arrancar y según {@code syncCron}); el botón del admin funciona siempre
 * @param syncCron    cada cuánto se sincronizan los precios
 * @param country     país de la tienda, que fija la moneda (es → euros)
 * @param baseUrl     raíz de la API (se cambia en tests)
 * @param timeout     tiempo máximo de cada petición a Steam
 */
@ConfigurationProperties("app.steam")
public record SteamProperties(boolean syncEnabled, String syncCron, String country, String baseUrl, Duration timeout) {
}
