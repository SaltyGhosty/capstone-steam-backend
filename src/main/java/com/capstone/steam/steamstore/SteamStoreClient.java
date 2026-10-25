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
 * Client dell'API pubblica dello store di Steam ({@code /api/appdetails}). Non richiede chiave,
 * ma Steam limita a circa 200 richieste ogni 5 minuti: i prezzi si chiedono a blocchi.
 *
 * <p>Steam risponde sempre 200 con {@code {"<appid>": {"success": bool, "data": ...}}}; un gioco
 * che non esiste arriva con {@code success: false}, e uno gratuito senza {@code price_overview}.
 */
@Component
public class SteamStoreClient {

    /** Quanti AppID si chiedono in ogni chiamata dei prezzi. */
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

    /** Prezzo attuale di ogni AppID. Quelli che Steam non conosce o che non hanno prezzo non compaiono nella mappa. */
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
     * Scheda del gioco in una lingua dell'interfaccia (es, en, it). Vuota se Steam non lo conosce.
     * La data di uscita si capisce solo nella scheda in inglese.
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

    /** Steam manda la descrizione con entità HTML (&amp;quot;...) e a volte tag. */
    static String plainText(String html) {
        String text = HtmlUtils.htmlUnescape(html.replaceAll("<[^>]+>", " "));
        return text.replaceAll("\\s+", " ").trim();
    }

    static LocalDate releaseDate(String text) {
        for (DateTimeFormatter format : RELEASE_FORMATS) {
            try {
                return LocalDate.parse(text.trim(), format);
            } catch (DateTimeParseException ignored) {
                // si prova il formato successivo
            }
        }
        return null; // "Coming soon", "Q4 2026"...
    }
}
