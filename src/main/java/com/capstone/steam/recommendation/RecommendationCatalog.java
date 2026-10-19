package com.capstone.steam.recommendation;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.capstone.steam.game.Game;
import com.capstone.steam.game.GameRepository;
import com.capstone.steam.game.GameStatus;
import com.capstone.steam.game.dto.GameSummaryResponse;
import com.capstone.steam.genre.Genre;

import tools.jackson.databind.json.JsonMapper;

/**
 * Lee el catálogo publicado en una transacción corta y lo devuelve ya copiado en objetos inmutables:
 * así la llamada al modelo (lenta) no retiene ninguna conexión de la base de datos.
 */
@Component
public class RecommendationCatalog {

    static final int MAX_DESCRIPTION = 160;
    static final int MAX_TITLE = 100;

    /** Las líneas del prompt y los juegos por id, listos para devolverse al cliente. */
    record Snapshot(List<String> lines, Map<Long, GameSummaryResponse> games) {
    }

    private final GameRepository games;
    private final JsonMapper json;

    public RecommendationCatalog(GameRepository games, JsonMapper json) {
        this.games = games;
        this.json = json;
    }

    @Transactional(readOnly = true)
    public Snapshot load(Locale locale) {
        List<Game> published = games.findWithDetailsByStatusOrderByIdAsc(GameStatus.PUBLISHED);
        List<String> lines = published.stream().map(g -> line(g, locale)).toList();
        Map<Long, GameSummaryResponse> byId = published.stream()
                .collect(Collectors.toUnmodifiableMap(Game::getId, GameSummaryResponse::from, (a, b) -> a));
        return new Snapshot(lines, byId);
    }

    /** Un juego como una línea JSON: Jackson escapa todo, así un título no puede abrir una línea nueva. */
    private String line(Game g, Locale locale) {
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("id", g.getId());
        fields.put("title", flatten(g.getTitle(), MAX_TITLE));
        fields.put("genres", g.getGenres().stream().map(Genre::getSlug).sorted().toList());
        fields.put("price", g.getFinalPrice());
        fields.put("positive", g.getPositivePercent());
        fields.put("reviews", g.getReviewCount());
        fields.put("description", flatten(g.getDescription(locale), MAX_DESCRIPTION));
        return json.writeValueAsString(fields);
    }

    /**
     * Texto de la tienda aplanado: sin caracteres de control ni saltos de línea, con los espacios
     * colapsados, sin {@code <} ni {@code >} (para que no cierre el delimitador del catálogo) y cortado.
     */
    static String flatten(String text, int max) {
        if (text == null) {
            return "";
        }
        String flat = text.replaceAll("[\\p{Cntrl}\\s<>]+", " ").strip();
        return flat.length() <= max ? flat : flat.substring(0, max).stripTrailing();
    }
}
