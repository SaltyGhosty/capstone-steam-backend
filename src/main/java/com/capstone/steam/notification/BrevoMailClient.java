package com.capstone.steam.notification;

import java.net.http.HttpClient;
import java.time.Duration;
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
 * Envío por la API HTTP de Brevo ({@code BREVO_API_KEY}). Render free bloquea el SMTP saliente
 * (puertos 25, 465 y 587) desde septiembre de 2025, así que en producción el correo va por HTTPS.
 * Sin clave, {@link EmailService} sigue usando el SMTP de Gmail (desarrollo en local).
 *
 * <p>El remitente ({@code MAIL_FROM}) tiene que estar verificado en Brevo. La clave solo viaja en la
 * cabecera {@code api-key}: no sale en logs ni en errores.
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

    /** Lanza {@code RestClientException} si Brevo no acepta el correo; {@link #isRetryable} dice si merece otro intento. */
    public void send(String fromEmail, String fromName, String to, String subject, String html) {
        String body = json.writeValueAsString(Map.of(
                "sender", Map.of("name", fromName, "email", fromEmail),
                "to", List.of(Map.of("email", to)),
                "subject", subject,
                "htmlContent", html));
        http.post()
                .uri(url)
                .header("api-key", apiKey)
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .toBodilessEntity();
    }

    /** Red caída o lenta, 429 y 5xx se reintentan; una clave o un remitente incorrectos (4xx) no cambian por esperar. */
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
