package com.capstone.steam.steamstore;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.HtmlUtils;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Cliente de la API pública de la tienda de Steam ({@code /api/appdetails}). No necesita clave,
 * pero Steam limita a unas 200 peticiones cada 5 minutos: los precios se piden en lotes.
 *
 * <p>Steam responde siempre 200 con {@code {"<appid>": {"success": bool, "data": ...}}}; un juego
 * que no existe llega con {@code success: false}, y uno gratuito sin {@code price_overview}.
 */
@Component
public class SteamStoreClient {

    /** Cuántos AppID se piden en cada llamada de precios. */
    static final int PRICE_BATCH = 50;

    private static final List<DateTimeFormatter> RELEASE_FORMATS = List.of(
            DateTimeFormatter.ofPattern("d MMM, yyyy", Locale.ENGLISH),
            DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.ENGLISH),
            DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH));

    private final RestClient http;
    private final JsonMapper json;
    private final String country;

    public SteamStoreClient(RestClient steamRestClient, JsonMapper json, SteamProperties properties) {
        this.http = steamRestClient;
        this.json = json;
        this.country = properties.country();
    }

    /** Precio actual de cada AppID. Los que Steam no conoce o no tienen precio no aparecen en el mapa. */
    public Map<Integer, SteamPrice> prices(Collection<Integer> appIds) {
        List<Integer> ids = new ArrayList<>(appIds);
        Map<Integer, SteamPrice> result = new HashMap<>();
        for (int from = 0; from < ids.size(); from += PRICE_BATCH) {
            List<Integer> batch = ids.subList(from, Math.min(from + PRICE_BATCH, ids.size()));
            String joined = batch.stream().map(String::valueOf).collect(Collectors.joining(","));
            JsonNode root = get("/api/appdetails?appids={ids}&cc={cc}&filters=price_overview", joined, country);
            for (Integer id : batch) {
                JsonNode entry = root.path(String.valueOf(id));
                if (entry.path("success").asBoolean(false)) {
                    price(entry.path("data").path("price_overview")).ifPresent(p -> result.put(id, p));
                }
            }
        }
        return result;
    }

    /**
     * Ficha del juego en un idioma de la interfaz (es, en, it). Vacío si Steam no lo conoce.
     * La fecha de lanzamiento solo se entiende en la ficha en inglés.
     */
    public Optional<SteamApp> app(int appId, String lang) {
        JsonNode entry = get("/api/appdetails?appids={id}&cc={cc}&l={lang}", appId, country, steamLanguage(lang))
                .path(String.valueOf(appId));
        if (!entry.path("success").asBoolean(false)) {
            return Optional.empty();
        }
        JsonNode data = entry.path("data");
        List<String> genres = new ArrayList<>();
        data.path("genres").forEach(g -> genres.add(g.path("id").asString("")));
        return Optional.of(new SteamApp(
                appId,
                data.path("name").asString(""),
                plainText(data.path("short_description").asString("")),
                releaseDate(data.path("release_date").path("date").asString("")),
                genres,
                data.path("is_free").asBoolean(false),
                price(data.path("price_overview")).orElse(null)));
    }

    private JsonNode get(String uri, Object... vars) {
        try {
            String body = http.get().uri(uri, vars).retrieve().body(String.class);
            return json.readTree(body == null ? "{}" : body);
        } catch (RestClientException | JacksonException e) {
            throw new SteamUnavailableException(e);
        }
    }

    private static Optional<SteamPrice> price(JsonNode node) {
        if (node.isMissingNode() || !node.has("initial")) {
            return Optional.empty();
        }
        return Optional.of(new SteamPrice(
                node.path("currency").asString(""),
                cents(node.path("initial").asLong()),
                cents(node.path("final").asLong()),
                node.path("discount_percent").asInt(0)));
    }

    private static BigDecimal cents(long cents) {
        return BigDecimal.valueOf(cents, 2);
    }

    static String steamLanguage(String lang) {
        return switch (lang == null ? "" : lang) {
            case "en" -> "english";
            case "it" -> "italian";
            default -> "spanish";
        };
    }

    /** Steam manda la descripción con entidades HTML (&amp;quot;...) y a veces etiquetas. */
    static String plainText(String html) {
        String text = HtmlUtils.htmlUnescape(html.replaceAll("<[^>]+>", " "));
        return text.replaceAll("\\s+", " ").trim();
    }

    static LocalDate releaseDate(String text) {
        for (DateTimeFormatter format : RELEASE_FORMATS) {
            try {
                return LocalDate.parse(text.trim(), format);
            } catch (DateTimeParseException ignored) {
                // se prueba el siguiente formato
            }
        }
        return null; // "Coming soon", "Q4 2026"...
    }
}
