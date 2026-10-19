package com.capstone.steam.recommendation;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import com.capstone.steam.recommendation.dto.ChatMessage;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

/**
 * Cliente de {@code POST /chat/completions} de OpenRouter. La clave solo viaja en la cabecera
 * Authorization: no se escribe en logs ni en mensajes de error, y tampoco el texto de la conversación.
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
        Map<String, Object> body = Map.of(
                "model", properties.model(),
                "messages", chat,
                "response_format", Map.of("type", "json_object"),
                "temperature", 0.7,
                "max_tokens", 400);

        long start = System.nanoTime();
        try {
            String raw = http.post()
                    .uri(properties.baseUrl() + "/chat/completions")
                    .header("Authorization", "Bearer " + properties.apiKey())
                    .header("X-Title", "ARCADE")
                    .body(body)
                    .retrieve()
                    .body(String.class);
            String content = json.readTree(raw).path("choices").path(0).path("message").path("content").asString(null);
            if (content == null || content.isBlank()) {
                log.warn("OpenRouter: respuesta sin contenido (modelo={})", properties.model());
                throw new AssistantUnavailableException();
            }
            log.info("OpenRouter: modelo={} mensajes={} duracion={}ms", properties.model(), messages.size(),
                    (System.nanoTime() - start) / 1_000_000);
            return content;
        } catch (RestClientException | JacksonException | IllegalArgumentException e) {
            // solo la clase: el mensaje podría arrastrar cabeceras o el cuerpo de la petición
            log.warn("OpenRouter: fallo {} (modelo={}, duracion={}ms)", e.getClass().getSimpleName(),
                    properties.model(), (System.nanoTime() - start) / 1_000_000);
            throw new AssistantUnavailableException(e);
        }
    }
}
