package com.capstone.steam.recommendation;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Conexión con OpenRouter (API compatible con OpenAI).
 *
 * @param apiKey  clave de OpenRouter; vacía = el asistente está desactivado
 * @param model   modelo, por defecto uno {@code :free}
 * @param baseUrl raíz de la API (se cambia en tests)
 * @param timeout tiempo máximo de cada petición
 */
@ConfigurationProperties("app.openrouter")
public record OpenRouterProperties(String apiKey, String model, String baseUrl, Duration timeout) {

    public boolean enabled() {
        return apiKey != null && !apiKey.isBlank();
    }

    /** Nunca imprime la clave. */
    @Override
    public String toString() {
        return "OpenRouterProperties[apiKey=" + (enabled() ? "***" : "(vacía)") + ", model=" + model
                + ", baseUrl=" + baseUrl + ", timeout=" + timeout + "]";
    }
}
