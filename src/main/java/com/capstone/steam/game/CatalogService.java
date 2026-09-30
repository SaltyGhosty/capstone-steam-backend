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

/** Lectura pública del escaparate: solo juegos PUBLISHED. */
@Service
@Transactional(readOnly = true)
public class CatalogService {

    private final GameCriteriaRepository gameCriteriaRepository;
    private final GameRepository gameRepository;

    public CatalogService(GameCriteriaRepository gameCriteriaRepository, GameRepository gameRepository) {
        this.gameCriteriaRepository = gameCriteriaRepository;
        this.gameRepository = gameRepository;
    }

    public PageResponse<GameSummaryResponse> search(GameSearchCriteria criteria, GameSort sort, int page, int size) {
        if (criteria.minPrice() != null && criteria.maxPrice() != null
                && criteria.minPrice().compareTo(criteria.maxPrice()) > 0) {
            throw new BadRequestException("minPrice no puede ser mayor que maxPrice");
        }
        var result = gameCriteriaRepository.search(criteria, sort, PageRequest.of(page, size));
        return PageResponse.of(result, GameSummaryResponse::from);
    }

    public GameDetailResponse getBySlug(String slug) {
        return gameRepository.findBySlug(slug)
                .filter(Game::isPublished)
                .map(GameDetailResponse::from)
                .orElseThrow(() -> NotFoundException.of("Juego", slug));
    }
}
