package com.capstone.steam.steamstore;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import com.capstone.steam.common.exception.ConflictException;
import com.capstone.steam.common.exception.NotFoundException;
import com.capstone.steam.game.Game;
import com.capstone.steam.game.GameManagementService;
import com.capstone.steam.game.GameRepository;
import com.capstone.steam.game.dto.GameDetailResponse;
import com.capstone.steam.game.dto.GameRequest;
import com.capstone.steam.genre.Genre;
import com.capstone.steam.genre.GenreRepository;

/**
 * Alta de un juego a partir de su ficha de Steam: título, descripción en es/en/it, fecha, géneros,
 * portada y precio. Queda como borrador del developer, enlazado por AppID para sincronizar el precio.
 */
@Service
public class SteamImportService {

    /** Géneros de Steam (id) que existen en la tienda (slug). El resto de géneros de Steam se ignoran. */
    static final Map<String, String> GENRES = Map.of(
            "1", "action",
            "25", "adventure",
            "3", "rpg",
            "2", "strategy",
            "28", "simulation",
            "18", "sports",
            "9", "racing",
            "23", "indie");

    private final SteamStoreClient steam;
    private final GameManagementService gameService;
    private final GameRepository gameRepository;
    private final GenreRepository genreRepository;
    private final TransactionTemplate tx;

    public SteamImportService(SteamStoreClient steam, GameManagementService gameService,
            GameRepository gameRepository, GenreRepository genreRepository, TransactionTemplate tx) {
        this.steam = steam;
        this.gameService = gameService;
        this.gameRepository = gameRepository;
        this.genreRepository = genreRepository;
        this.tx = tx;
    }

    public GameDetailResponse importGame(Long developerId, int appId) {
        if (gameRepository.existsBySteamAppId(appId)) {
            throw new ConflictException("game.steamAppIdTaken", String.valueOf(appId));
        }
        // primero Steam (fuera de la transacción); la ficha en inglés manda en título y fecha
        SteamApp en = steam.app(appId, "en").orElseThrow(() -> new NotFoundException("steam.notFound",
                String.valueOf(appId)));
        SteamApp es = steam.app(appId, "es").orElse(en);
        SteamApp it = steam.app(appId, "it").orElse(en);

        SteamPrice price = en.price();
        Set<Long> genreIds = genreRepository.findBySlugIn(en.genreIds().stream()
                .map(GENRES::get).filter(Objects::nonNull).distinct().toList())
                .stream().map(Genre::getId).collect(Collectors.toSet());
        GameRequest request = new GameRequest(
                truncate(en.name(), 150),
                blankToNull(es.description()),
                blankToNull(en.description()),
                blankToNull(it.description()),
                price == null ? BigDecimal.ZERO : price.initial(),
                price == null ? 0 : price.discountPercent(),
                en.releaseDate(),
                coverUrl(appId),
                appId,
                genreIds,
                null);

        return tx.execute(status -> {
            GameDetailResponse created = gameService.create(developerId, request);
            if (price == null) {
                return created;
            }
            Game game = gameRepository.findById(created.id()).orElseThrow();
            game.applySteamPrice(price.initial(), price.discountPercent(), Instant.now());
            return GameDetailResponse.from(gameRepository.saveAndFlush(game));
        });
    }

    /** Portada vertical del CDN de Steam (la que usa el resto del catálogo). */
    static String coverUrl(int appId) {
        return "https://cdn.cloudflare.steamstatic.com/steam/apps/" + appId + "/library_600x900_2x.jpg";
    }

    private static String truncate(String text, int max) {
        return text.length() <= max ? text : text.substring(0, max);
    }

    private static String blankToNull(String text) {
        return text == null || text.isBlank() ? null : text;
    }
}
