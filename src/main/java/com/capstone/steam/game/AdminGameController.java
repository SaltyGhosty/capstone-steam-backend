package com.capstone.steam.game;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.capstone.steam.common.dto.PageResponse;
import com.capstone.steam.common.util.SortWhitelist;
import com.capstone.steam.game.dto.GameDetailResponse;
import com.capstone.steam.game.dto.StatusRequest;

import jakarta.validation.Valid;

/** Moderación del catálogo (solo ADMIN). */
@RestController
@RequestMapping("/api/admin/games")
public class AdminGameController {

    private final GameManagementService gameService;

    public AdminGameController(GameManagementService gameService) {
        this.gameService = gameService;
    }

    @GetMapping
    public PageResponse<GameDetailResponse> list(@RequestParam(required = false) GameStatus status,
            @PageableDefault(size = 20, sort = "id", direction = Sort.Direction.ASC) Pageable pageable) {
        return gameService.listAll(status, SortWhitelist.require(pageable, GameManagementService.SORTABLE));
    }

    @PatchMapping("/{id}/status")
    public GameDetailResponse moderate(@PathVariable Long id, @Valid @RequestBody StatusRequest request) {
        return gameService.moderate(id, request.status());
    }
}
