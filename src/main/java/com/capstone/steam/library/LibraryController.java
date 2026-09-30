package com.capstone.steam.library;

import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.capstone.steam.common.dto.PageResponse;
import com.capstone.steam.library.dto.GameOwnershipResponse;
import com.capstone.steam.library.dto.LibraryEntryResponse;
import com.capstone.steam.security.AppUserPrincipal;

@RestController
public class LibraryController {

    private final LibraryService libraryService;

    public LibraryController(LibraryService libraryService) {
        this.libraryService = libraryService;
    }

    @GetMapping("/api/library")
    public PageResponse<LibraryEntryResponse> list(@AuthenticationPrincipal AppUserPrincipal user, Pageable pageable) {
        return libraryService.list(user.getId(), pageable);
    }

    /** ¿Lo tengo? ¿Está en mi carrito/wishlist? ¿Lo he reseñado? */
    @GetMapping("/api/library/status/{gameId}")
    public GameOwnershipResponse ownership(@AuthenticationPrincipal AppUserPrincipal user, @PathVariable Long gameId) {
        return libraryService.ownership(user.getId(), gameId);
    }

    @PostMapping("/api/admin/users/{userId}/library/{gameId}")
    @ResponseStatus(HttpStatus.CREATED)
    public LibraryEntryResponse grant(@PathVariable Long userId, @PathVariable Long gameId) {
        return libraryService.grant(userId, gameId);
    }
}
