package com.capstone.steam.game;

import java.util.Set;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.capstone.steam.common.dto.PageResponse;
import com.capstone.steam.common.exception.BadRequestException;
import com.capstone.steam.common.exception.ConflictException;
import com.capstone.steam.common.exception.ForbiddenException;
import com.capstone.steam.common.exception.NotFoundException;
import com.capstone.steam.common.util.Slugs;
import com.capstone.steam.game.dto.GameDetailResponse;
import com.capstone.steam.game.dto.GameRequest;
import com.capstone.steam.genre.GenreService;
import com.capstone.steam.user.UserRepository;

/**
 * Gestión de juegos: el developer solo actúa sobre los suyos (403 si no es el dueño)
 * y el admin modera el estado de cualquiera.
 *
 * <p>Estados: DRAFT --publish--> PUBLISHED --unpublish--> DRAFT (developer).
 * Cualquier estado --> DELISTED y DELISTED --> PUBLISHED solo los hace un admin.
 */
@Service
@Transactional
public class GameManagementService {

    /** Campos por los que se pueden ordenar los listados de admin y de estudio ({@code ?sort=}). */
    public static final Set<String> SORTABLE = Set.of(
            "id", "title", "price", "discountPercent", "releaseDate", "status", "salesCount", "createdAt", "updatedAt");

    private final GameRepository gameRepository;
    private final UserRepository userRepository;
    private final GenreService genreService;

    public GameManagementService(GameRepository gameRepository, UserRepository userRepository,
            GenreService genreService) {
        this.gameRepository = gameRepository;
        this.userRepository = userRepository;
        this.genreService = genreService;
    }

    // ------------------------------ Developer ------------------------------

    @Transactional(readOnly = true)
    public PageResponse<GameDetailResponse> listOwn(Long developerId, Pageable pageable) {
        return PageResponse.of(gameRepository.findByDeveloperId(developerId, pageable), GameDetailResponse::from);
    }

    @Transactional(readOnly = true)
    public GameDetailResponse getOwn(Long developerId, Long gameId) {
        return GameDetailResponse.from(findOwned(developerId, gameId));
    }

    public GameDetailResponse create(Long developerId, GameRequest request) {
        Game game = new Game(request.title().trim(), uniqueSlug(request.title()),
                userRepository.getReferenceById(developerId), request.price());
        applyChanges(game, request);
        return GameDetailResponse.from(gameRepository.saveAndFlush(game));
    }

    /** El slug no cambia al renombrar: las URLs ya compartidas siguen funcionando. */
    public GameDetailResponse update(Long developerId, Long gameId, GameRequest request) {
        Game game = findOwned(developerId, gameId);
        if (request.version() != null && !request.version().equals(game.getVersion())) {
            throw new ConflictException("game.modified");
        }
        game.setTitle(request.title().trim());
        game.setPrice(request.price());
        applyChanges(game, request);
        // flush para que la respuesta lleve la versión ya incrementada
        return GameDetailResponse.from(gameRepository.saveAndFlush(game));
    }

    public GameDetailResponse publish(Long developerId, Long gameId) {
        Game game = findOwned(developerId, gameId);
        if (game.getStatus() != GameStatus.DRAFT) {
            throw new ConflictException("game.publishOnlyDraft", game.getStatus().name());
        }
        if (game.getGenres().isEmpty()) {
            throw new BadRequestException("game.needsGenre");
        }
        game.setStatus(GameStatus.PUBLISHED);
        return GameDetailResponse.from(gameRepository.saveAndFlush(game));
    }

    public GameDetailResponse unpublish(Long developerId, Long gameId) {
        Game game = findOwned(developerId, gameId);
        if (game.getStatus() != GameStatus.PUBLISHED) {
            throw new ConflictException("game.notPublished");
        }
        game.setStatus(GameStatus.DRAFT);
        return GameDetailResponse.from(gameRepository.saveAndFlush(game));
    }

    /** Solo borradores sin ventas: lo vendido tiene que seguir en las bibliotecas y en los pedidos. */
    public void delete(Long developerId, Long gameId) {
        Game game = findOwned(developerId, gameId);
        if (game.getStatus() != GameStatus.DRAFT || game.getSalesCount() > 0) {
            throw new ConflictException("game.deleteOnlyDraft");
        }
        gameRepository.delete(game);
    }

    /** Fija la portada ya subida (URL https del almacén). Sube la versión del juego. */
    public GameDetailResponse setCover(Long developerId, Long gameId, String url) {
        Game game = findOwned(developerId, gameId);
        game.setCoverUrl(url);
        return GameDetailResponse.from(gameRepository.saveAndFlush(game));
    }

    /** Descuento masivo sobre todos los juegos publicados del developer (UPDATE atómico). */
    public int applyDiscountToAllPublished(Long developerId, int percent) {
        return gameRepository.bulkDiscountForDeveloper(developerId, percent, GameStatus.PUBLISHED);
    }

    // -------------------------------- Admin --------------------------------

    @Transactional(readOnly = true)
    public PageResponse<GameDetailResponse> listAll(GameStatus status, Pageable pageable) {
        Page<Game> page = status == null ? gameRepository.findAll(pageable) : gameRepository.findByStatus(status, pageable);
        return PageResponse.of(page, GameDetailResponse::from);
    }

    public GameDetailResponse moderate(Long gameId, GameStatus target) {
        Game game = gameRepository.findWithDetailsById(gameId).orElseThrow(() -> NotFoundException.of("game", gameId));
        switch (target) {
            case DELISTED -> game.setStatus(GameStatus.DELISTED);
            case PUBLISHED -> {
                if (game.getStatus() != GameStatus.DELISTED) {
                    throw new ConflictException("game.republishOnlyDelisted");
                }
                if (game.getDeveloper().isDeleted()) {
                    throw new ConflictException("game.developerDeleted");
                }
                game.setStatus(GameStatus.PUBLISHED);
            }
            case DRAFT -> throw new BadRequestException("game.draftByDeveloper");
        }
        return GameDetailResponse.from(gameRepository.saveAndFlush(game));
    }

    /** El admin ve cualquier juego; 404 si no existe (se usa antes de subir una portada). */
    @Transactional(readOnly = true)
    public GameDetailResponse getAny(Long gameId) {
        return GameDetailResponse.from(
                gameRepository.findWithDetailsById(gameId).orElseThrow(() -> NotFoundException.of("game", gameId)));
    }

    public GameDetailResponse setCoverAsAdmin(Long gameId, String url) {
        Game game = gameRepository.findWithDetailsById(gameId).orElseThrow(() -> NotFoundException.of("game", gameId));
        game.setCoverUrl(url);
        return GameDetailResponse.from(gameRepository.saveAndFlush(game));
    }

    // ------------------------------------------------------------------------

    private Game findOwned(Long developerId, Long gameId) {
        Game game = gameRepository.findWithDetailsById(gameId).orElseThrow(() -> NotFoundException.of("game", gameId));
        if (!game.isOwnedBy(developerId)) {
            throw new ForbiddenException("game.notOwner");
        }
        return game;
    }

    private void applyChanges(Game game, GameRequest request) {
        game.setDescription(request.description());
        game.setDescriptionEn(request.descriptionEn());
        game.setDescriptionIt(request.descriptionIt());
        game.setDiscountPercent(request.discountPercent() == null ? 0 : request.discountPercent());
        game.setReleaseDate(request.releaseDate());
        game.setCoverUrl(request.coverUrl() == null || request.coverUrl().isBlank() ? null : request.coverUrl());
        if (request.steamAppId() != null && !request.steamAppId().equals(game.getSteamAppId())
                && gameRepository.existsBySteamAppId(request.steamAppId())) {
            throw new ConflictException("game.steamAppIdTaken", String.valueOf(request.steamAppId()));
        }
        game.setSteamAppId(request.steamAppId());
        game.setGenres(genreService.resolve(request.genreIds()));
    }

    private String uniqueSlug(String title) {
        String base = Slugs.slugify(title);
        String slug = base;
        for (int n = 2; gameRepository.existsBySlug(slug); n++) {
            slug = base + "-" + n;
        }
        return slug; // una carrera entre dos altas la frena el UNIQUE de la BD (-> 409)
    }
}
