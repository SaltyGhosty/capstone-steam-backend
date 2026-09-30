package com.capstone.steam.game;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.capstone.steam.common.dto.PageResponse;
import com.capstone.steam.game.dto.BulkUpdateResponse;
import com.capstone.steam.game.dto.DiscountRequest;
import com.capstone.steam.game.dto.GameDetailResponse;
import com.capstone.steam.game.dto.GameRequest;
import com.capstone.steam.security.AppUserPrincipal;

import jakarta.validation.Valid;

/** Panel del developer (regla /api/developer/** -> ROLE_DEVELOPER). */
@RestController
@RequestMapping("/api/developer/games")
public class DeveloperGameController {

    private final GameManagementService gameService;

    public DeveloperGameController(GameManagementService gameService) {
        this.gameService = gameService;
    }

    @GetMapping
    public PageResponse<GameDetailResponse> listOwn(@AuthenticationPrincipal AppUserPrincipal dev,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return gameService.listOwn(dev.getId(), pageable);
    }

    @GetMapping("/{id}")
    public GameDetailResponse get(@AuthenticationPrincipal AppUserPrincipal dev, @PathVariable Long id) {
        return gameService.getOwn(dev.getId(), id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public GameDetailResponse create(@AuthenticationPrincipal AppUserPrincipal dev,
            @Valid @RequestBody GameRequest request) {
        return gameService.create(dev.getId(), request);
    }

    @PutMapping("/{id}")
    public GameDetailResponse update(@AuthenticationPrincipal AppUserPrincipal dev, @PathVariable Long id,
            @Valid @RequestBody GameRequest request) {
        return gameService.update(dev.getId(), id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal AppUserPrincipal dev, @PathVariable Long id) {
        gameService.delete(dev.getId(), id);
    }

    @PostMapping("/{id}/publish")
    public GameDetailResponse publish(@AuthenticationPrincipal AppUserPrincipal dev, @PathVariable Long id) {
        return gameService.publish(dev.getId(), id);
    }

    @PostMapping("/{id}/unpublish")
    public GameDetailResponse unpublish(@AuthenticationPrincipal AppUserPrincipal dev, @PathVariable Long id) {
        return gameService.unpublish(dev.getId(), id);
    }

    @PostMapping("/discount")
    public BulkUpdateResponse discountAll(@AuthenticationPrincipal AppUserPrincipal dev,
            @Valid @RequestBody DiscountRequest request) {
        return new BulkUpdateResponse(gameService.applyDiscountToAllPublished(dev.getId(), request.percent()));
    }
}
