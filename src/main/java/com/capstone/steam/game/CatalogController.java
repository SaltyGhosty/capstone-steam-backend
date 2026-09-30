package com.capstone.steam.game;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.capstone.steam.common.dto.PageResponse;
import com.capstone.steam.game.dto.GameDetailResponse;
import com.capstone.steam.game.dto.GameSummaryResponse;
import com.capstone.steam.game.search.GameSearchCriteria;
import com.capstone.steam.game.search.GameSort;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/** Escaparate público (GET /api/games/** es permitAll en SecurityConfig). */
@RestController
@RequestMapping("/api/games")
public class CatalogController {

    private final CatalogService catalogService;

    public CatalogController(CatalogService catalogService) {
        this.catalogService = catalogService;
    }

    /**
     * Ejemplo: {@code GET /api/games?q=hollow&genres=indie,platformer&maxPrice=15&onSale=true&sort=price_asc&page=0&size=20}
     */
    @GetMapping
    public PageResponse<GameSummaryResponse> search(
            @Valid GameSearchCriteria criteria,
            @RequestParam(defaultValue = "newest") String sort,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return catalogService.search(criteria, GameSort.fromParam(sort), page, size);
    }

    @GetMapping("/{slug}")
    public GameDetailResponse getBySlug(@PathVariable String slug) {
        return catalogService.getBySlug(slug);
    }
}
