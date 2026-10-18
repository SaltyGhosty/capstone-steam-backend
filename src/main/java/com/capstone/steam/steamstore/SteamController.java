package com.capstone.steam.steamstore;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.capstone.steam.game.dto.GameDetailResponse;
import com.capstone.steam.security.AppUserPrincipal;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * Integración con la tienda de Steam: importar un juego (developer) y sincronizar precios (admin).
 * Los permisos los ponen las reglas de URL: /api/developer/** y /api/admin/**.
 */
@RestController
public class SteamController {

    public record ImportRequest(@NotNull @Positive Integer steamAppId) {
    }

    private final SteamImportService importService;
    private final SteamPriceSyncService syncService;

    public SteamController(SteamImportService importService, SteamPriceSyncService syncService) {
        this.importService = importService;
        this.syncService = syncService;
    }

    @PostMapping("/api/developer/games/import")
    @ResponseStatus(HttpStatus.CREATED)
    public GameDetailResponse importGame(@AuthenticationPrincipal AppUserPrincipal dev,
            @Valid @RequestBody ImportRequest request) {
        return importService.importGame(dev.getId(), request.steamAppId());
    }

    @PostMapping("/api/admin/steam/sync")
    public SyncResult sync() {
        return syncService.sync();
    }
}
