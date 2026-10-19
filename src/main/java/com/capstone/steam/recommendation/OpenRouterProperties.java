package com.capstone.steam.recommendation;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Conexión con OpenRouter (API compatible con OpenAI).
 *
 * @param apiKey        clave de OpenRouter; vacía = el asistente está desactivado
 * @param model         modelo, o varios separados por comas (el primero es el preferido y los demás, la reserva)
 * @param baseUrl       raíz de la API (se cambia en tests)
 * @param timeout       tiempo máximo de cada petición
 * @param globalPerHour tope de mensajes por hora entre todos los usuarios (por defecto 200)
 * @param cooldown      cuánto se deja de llamar al modelo tras un 429 de OpenRouter (por defecto 5 min)
 */
@ConfigurationProperties("app.openrouter")
public record OpenRouterProperties(String apiKey, String model, String baseUrl, Duration timeout,
        Integer globalPerHour, Duration cooldown) {

    public static final int DEFAULT_GLOBAL_PER_HOUR = 200;
    public static final Duration DEFAULT_COOLDOWN = Duration.ofMinutes(5);

    public OpenRouterProperties {
        if (globalPerHour == null || globalPerHour < 1) {
            globalPerHour = DEFAULT_GLOBAL_PER_HOUR;
        }
        if (cooldown == null) {
            cooldown = DEFAULT_COOLDOWN;
        }
    }

    public boolean enabled() {
        return apiKey != null && !apiKey.isBlank();
    }

    /** Los modelos de {@code model}: partido por comas, sin espacios sobrantes ni vacíos. */
    public List<String> models() {
        if (model == null) {
            return List.of();
        }
        return Arrays.stream(model.split(",")).map(String::trim).filter(m -> !m.isEmpty()).toList();
    }

    /** Nunca imprime la clave. */
    @Override
    public String toString() {
        return "OpenRouterProperties[apiKey=" + (enabled() ? "***" : "(vacía)") + ", model=" + model
                + ", baseUrl=" + baseUrl + ", timeout=" + timeout + ", globalPerHour=" + globalPerHour
                + ", cooldown=" + cooldown + "]";
    }
}
