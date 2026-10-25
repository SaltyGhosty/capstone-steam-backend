package com.capstone.steam.recommendation;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.capstone.steam.game.Game;
import com.capstone.steam.game.GameRepository;
import com.capstone.steam.game.GameStatus;
import com.capstone.steam.game.dto.GameSummaryResponse;
import com.capstone.steam.genre.Genre;
import com.capstone.steam.library.LibraryRepository;

import tools.jackson.databind.json.JsonMapper;

/**
 * Lee el catálogo publicado (sin los juegos del usuario) en una transacción corta y lo devuelve ya copiado en objetos inmutables:
 * así la llamada al modelo (lenta) no retiene ninguna conexión de la base de datos.
 */
@Component
public class RecommendationCatalog {

    static final int MAX_DESCRIPTION = 160;
    static final int MAX_TITLE = 100;
    static final int MAX_GAMES = 200;

    /** Las líneas del prompt y los juegos por id, listos para devolverse al cliente. */
    record Snapshot(List<String> lines, Map<Long, GameSummaryResponse> games) {
    }

    private final GameRepository games;
    private final LibraryRepository library;
    private final JsonMapper json;

    public RecommendationCatalog(GameRepository games, LibraryRepository library, JsonMapper json) {
        this.games = games;
        this.library = library;
        this.json = json;
    }

    @Transactional(readOnly = true)
    public Snapshot load(long userId, Locale locale) {
        // lo que el usuario ya tiene (biblioteca) o desarrolla no se le recomienda
        Set<Long> owned = library.findGameIdsByUserId(userId);
        // tope para que el prompt no crezca sin fin: los más reseñados primero, y a igualdad el id menor
        List<Game> published = games.findWithDetailsByStatusOrderByIdAsc(GameStatus.PUBLISHED).stream()
                .filter(g -> !owned.contains(g.getId()) && !Long.valueOf(userId).equals(g.getDeveloper().getId()))
                .sorted(Comparator.comparingInt(Game::getReviewCount).reversed().thenComparing(Game::getId))
                .limit(MAX_GAMES)
                .toList();
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
