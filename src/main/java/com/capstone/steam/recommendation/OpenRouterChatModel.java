package com.capstone.steam.recommendation;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import com.capstone.steam.recommendation.dto.ChatMessage;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Client di {@code POST /chat/completions} di OpenRouter. La chiave viaggia solo nell'header
 * Authorization: non si scrive in log né in messaggi di errore, e nemmeno il testo della conversazione.
 */
@Component
public class OpenRouterChatModel implements ChatModel {

    private static final Logger log = LoggerFactory.getLogger(OpenRouterChatModel.class);

    private final RestClient http;
    private final JsonMapper json;
    private final OpenRouterProperties properties;

    public OpenRouterChatModel(RestClient openRouterRestClient, JsonMapper json, OpenRouterProperties properties) {
        this.http = openRouterRestClient;
        this.json = json;
        this.properties = properties;
    }

    @Override
    public String complete(String system, List<ChatMessage> messages) {
        if (!properties.enabled()) {
            throw new AssistantUnavailableException();
        }
        List<Map<String, String>> chat = new ArrayList<>();
        chat.add(Map.of("role", "system", "content", system));
        messages.forEach(m -> chat.add(Map.of("role", m.role().name(), "content", m.content())));
        List<String> models = properties.models();
        Map<String, Object> body = new LinkedHashMap<>();
        if (models.size() == 1) {
            body.put("model", models.get(0));
        } else {
            // elenco di riserva di OpenRouter: se il primo fallisce, prova il successivo
            body.put("models", models);
        }
        body.put("messages", chat);
        body.put("response_format", Map.of("type", "json_object"));
        body.put("temperature", 0.7);
        body.put("max_tokens", 1200);
        // ragionamento breve e fuori dalla risposta: alcuni modelli non permettono di disattivarlo
        body.put("reasoning", Map.of("effort", "low", "exclude", true));

        long start = System.nanoTime();
        try {
            String raw = http.post()
                    .uri(properties.baseUrl() + "/chat/completions")
                    .header("Authorization", "Bearer " + properties.apiKey())
                    .header("X-Title", "ARCADE")
                    .body(body)
                    .retrieve()
                    .body(String.class);
            JsonNode parsed = json.readTree(raw);
            String content = parsed.path("choices").path(0).path("message").path("content").asString(null);
            if (content == null || content.isBlank()) {
                log.warn("OpenRouter: respuesta sin contenido (modelo={})", properties.model());
                throw new AssistantUnavailableException();
            }
            String used = parsed.path("model").asString(properties.model());
            log.info("OpenRouter: modelo={} mensajes={} duracion={}ms", used, messages.size(),
                    (System.nanoTime() - start) / 1_000_000);
            return content;
        } catch (RestClientResponseException e) {
            // codice e error.message di OpenRouter, troncato; mai la chiave né la conversazione
            log.warn("OpenRouter: error {} ({}, modelo={}, duracion={}ms)", e.getStatusCode().value(),
                    errorMessage(e), properties.model(), (System.nanoTime() - start) / 1_000_000);
            if (e.getStatusCode().value() == 429) {
                throw new AssistantRateLimitedException(e);
            }
            throw new AssistantUnavailableException(e);
        } catch (RestClientException | JacksonException | IllegalArgumentException e) {
            // solo la classe: il messaggio potrebbe portarsi dietro header o il corpo della richiesta
            log.warn("OpenRouter: fallo {} (modelo={}, duracion={}ms)", e.getClass().getSimpleName(),
                    properties.model(), (System.nanoTime() - start) / 1_000_000);
            throw new AssistantUnavailableException(e);
        }
    }

    private String errorMessage(RestClientResponseException e) {
        try {
            String message = json.readTree(e.getResponseBodyAsString()).path("error").path("message").asString("");
            return message.length() > 200 ? message.substring(0, 200) : message;
        } catch (JacksonException | IllegalArgumentException ex) {
            return "";
        }
    }
}
