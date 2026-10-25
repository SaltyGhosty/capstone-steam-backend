package com.capstone.steam.notification;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import tools.jackson.databind.json.JsonMapper;

/**
 * Invio tramite l'API HTTP di Brevo ({@code BREVO_API_KEY}). Render free blocca l'SMTP in uscita
 * (porte 25, 465 e 587) da settembre 2025, quindi in produzione l'email passa per HTTPS.
 * Senza chiave, {@link EmailService} continua a usare l'SMTP di Gmail (sviluppo in locale).
 *
 * <p>Il mittente ({@code MAIL_FROM}) deve essere verificato su Brevo. La chiave viaggia solo nell'
 * header {@code api-key}: non compare né nei log né negli errori.
 */
@Component
public class BrevoMailClient {

    private final RestClient http;
    private final String apiKey;
    private final String url;
    private final JsonMapper json = JsonMapper.builder().build();

    @Autowired
    public BrevoMailClient(@Value("${app.mail.brevo.api-key:}") String apiKey,
            @Value("${app.mail.brevo.url:https://api.brevo.com/v3/smtp/email}") String url,
            @Value("${app.mail.brevo.timeout:PT15S}") Duration timeout) {
        this(restClient(timeout), apiKey, url);
    }

    BrevoMailClient(RestClient http, String apiKey, String url) {
        this.http = http;
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.url = url;
    }

    public boolean enabled() {
        return !apiKey.isEmpty();
    }

    public void send(String fromEmail, String fromName, String to, String subject, String html) {
        send(fromEmail, fromName, to, subject, html, null);
    }

    /**
     * Lancia {@code RestClientException} se Brevo non accetta l'email; {@link #isRetryable} dice se vale un altro tentativo.
     * {@code replyTo} (opzionale) è l'indirizzo a cui risponde il destinatario.
     */
    public void send(String fromEmail, String fromName, String to, String subject, String html, String replyTo) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("sender", Map.of("name", fromName, "email", fromEmail));
        payload.put("to", List.of(Map.of("email", to)));
        if (replyTo != null && !replyTo.isBlank()) {
            payload.put("replyTo", Map.of("email", replyTo));
        }
        payload.put("subject", subject);
        payload.put("htmlContent", html);
        String body = json.writeValueAsString(payload);
        http.post()
                .uri(url)
                .header("api-key", apiKey)
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .toBodilessEntity();
    }

    /** Rete assente o lenta, 429 e 5xx si ritentano; una chiave o un mittente errati (4xx) non cambiano aspettando. */
    static boolean isRetryable(Throwable ex) {
        if (ex instanceof ResourceAccessException) {
            return true;
        }
        if (ex instanceof RestClientResponseException response) {
            int status = response.getStatusCode().value();
            return status == 429 || status >= 500;
        }
        return false;
    }

    private static RestClient restClient(Duration timeout) {
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder().connectTimeout(timeout).build());
        factory.setReadTimeout(timeout);
        return RestClient.builder()
                .requestFactory(factory)
                .defaultHeader("User-Agent", "ARCADE-Capstone/1.0")
                .build();
    }
}
