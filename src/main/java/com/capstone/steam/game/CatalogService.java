package com.capstone.steam.game;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.capstone.steam.common.dto.PageResponse;
import com.capstone.steam.common.exception.BadRequestException;
import com.capstone.steam.common.exception.NotFoundException;
import com.capstone.steam.game.dto.GameDetailResponse;
import com.capstone.steam.game.dto.GameSummaryResponse;
import com.capstone.steam.game.search.GameCriteriaRepository;
import com.capstone.steam.game.search.GameSearchCriteria;
import com.capstone.steam.game.search.GameSort;

/** Lettura pubblica della vetrina: il catalogo contiene solo PUBLISHED; la scheda di un DELISTED la vedono i suoi proprietari. */
@Service
@Transactional(readOnly = true)
public class CatalogService {

    private final GameCriteriaRepository gameCriteriaRepository;
    private final GameRepository gameRepository;
    private final GameVisibility gameVisibility;

    public CatalogService(GameCriteriaRepository gameCriteriaRepository, GameRepository gameRepository,
            GameVisibility gameVisibility) {
        this.gameCriteriaRepository = gameCriteriaRepository;
        this.gameRepository = gameRepository;
        this.gameVisibility = gameVisibility;
    }

    public PageResponse<GameSummaryResponse> search(GameSearchCriteria criteria, GameSort sort, int page, int size) {
        if (criteria.minPrice() != null && criteria.maxPrice() != null
                && criteria.minPrice().compareTo(criteria.maxPrice()) > 0) {
            throw new BadRequestException("catalog.priceRange");
        }
        var result = gameCriteriaRepository.search(criteria, sort, PageRequest.of(page, size));
        return PageResponse.of(result, GameSummaryResponse::from);
    }

    /** {@code viewerId} è null senza sessione; chi non può vederla riceve lo stesso 404 che se non esistesse. */
    public GameDetailResponse getBySlug(String slug, Long viewerId, boolean viewerIsAdmin) {
        return gameRepository.findBySlug(slug)
                .filter(game -> gameVisibility.canView(game, viewerId, viewerIsAdmin))
                .map(GameDetailResponse::from)
                .orElseThrow(() -> NotFoundException.of("game", slug));
    }
}
