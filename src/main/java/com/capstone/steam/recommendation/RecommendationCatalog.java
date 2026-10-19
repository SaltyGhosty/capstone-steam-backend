package com.capstone.steam.recommendation;

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

/**
 * Lee el catálogo publicado en una transacción corta y lo devuelve ya copiado en objetos inmutables:
 * así la llamada al modelo (lenta) no retiene ninguna conexión de la base de datos.
 */
@Component
public class RecommendationCatalog {

    static final int MAX_DESCRIPTION = 160;

    /** Las líneas del prompt y los juegos por id, listos para devolverse al cliente. */
    record Snapshot(List<String> lines, Map<Long, GameSummaryResponse> games) {
    }

    private final GameRepository games;

    public RecommendationCatalog(GameRepository games) {
        this.games = games;
    }

    @Transactional(readOnly = true)
    public Snapshot load(Locale locale) {
        List<Game> published = games.findWithDetailsByStatusOrderByIdAsc(GameStatus.PUBLISHED);
        List<String> lines = published.stream().map(g -> line(g, locale)).toList();
        Map<Long, GameSummaryResponse> byId = published.stream()
                .collect(Collectors.toUnmodifiableMap(Game::getId, GameSummaryResponse::from, (a, b) -> a));
        return new Snapshot(lines, byId);
    }

    private static String line(Game g, Locale locale) {
        return g.getId() + " | " + g.getTitle() + " | "
                + g.getGenres().stream().map(Genre::getSlug).sorted().collect(Collectors.joining(",")) + " | "
                + g.getFinalPrice().toPlainString() + " | "
                + (g.getReviewCount() == 0 ? "no reviews" : g.getPositivePercent() + "% (" + g.getReviewCount() + ")")
                + " | " + shorten(g.getDescription(locale));
    }

    /** Una sola línea y como mucho 160 caracteres. */
    private static String shorten(String description) {
        if (description == null) {
            return "";
        }
        String flat = description.replaceAll("\\s+", " ").strip();
        return flat.length() <= MAX_DESCRIPTION ? flat : flat.substring(0, MAX_DESCRIPTION);
    }
}
