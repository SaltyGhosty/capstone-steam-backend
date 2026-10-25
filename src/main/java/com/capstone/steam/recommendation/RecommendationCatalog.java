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
 * Legge il catalogo pubblicato (senza i giochi dell'utente) in una transazione breve e lo restituisce già copiato in oggetti immutabili:
 * così la chiamata al modello (lenta) non trattiene nessuna connessione del database.
 */
@Component
public class RecommendationCatalog {

    static final int MAX_DESCRIPTION = 160;
    static final int MAX_TITLE = 100;
    static final int MAX_GAMES = 200;

    /** Le righe del prompt e i giochi per id, pronti da restituire al client. */
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
        // ciò che l'utente ha già (libreria) o sviluppa non gli viene raccomandato
        Set<Long> owned = library.findGameIdsByUserId(userId);
        // tetto perché il prompt non cresca all'infinito: prima i più recensiti e, a parità, l'id minore
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

    /** Un gioco come riga JSON: Jackson fa l'escape di tutto, così un titolo non può aprire una nuova riga. */
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
     * Testo dello store appiattito: senza caratteri di controllo né a capo, con gli spazi
     * compressi, senza {@code <} né {@code >} (perché non chiuda il delimitatore del catalogo) e troncato.
     */
    static String flatten(String text, int max) {
        if (text == null) {
            return "";
        }
        String flat = text.replaceAll("[\\p{Cntrl}\\s<>]+", " ").strip();
        return flat.length() <= max ? flat : flat.substring(0, max).stripTrailing();
    }
}
