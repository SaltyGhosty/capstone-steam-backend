package com.capstone.steam.recommendation;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Connessione con OpenRouter (API compatibile con OpenAI).
 *
 * @param apiKey        chiave di OpenRouter; vuota = l'assistente è disattivato
 * @param model         modello, o più modelli separati da virgole (il primo è il preferito e gli altri, la riserva)
 * @param baseUrl       radice dell'API (si cambia nei test)
 * @param timeout       tempo massimo di ogni richiesta
 * @param globalPerHour tetto di messaggi all'ora tra tutti gli utenti (per default 200)
 * @param cooldown      per quanto si smette di chiamare il modello dopo un 429 di OpenRouter (per default 5 min)
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

    /** I modelli di {@code model}: divisi per virgole, senza spazi in eccesso né voci vuote. */
    public List<String> models() {
        if (model == null) {
            return List.of();
        }
        return Arrays.stream(model.split(",")).map(String::trim).filter(m -> !m.isEmpty()).toList();
    }

    /** Non stampa mai la chiave. */
    @Override
    public String toString() {
        return "OpenRouterProperties[apiKey=" + (enabled() ? "***" : "(vacía)") + ", model=" + model
                + ", baseUrl=" + baseUrl + ", timeout=" + timeout + ", globalPerHour=" + globalPerHour
                + ", cooldown=" + cooldown + "]";
    }
}
