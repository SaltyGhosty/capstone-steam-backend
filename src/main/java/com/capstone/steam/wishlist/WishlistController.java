package com.capstone.steam.wishlist;

import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.capstone.steam.common.dto.PageResponse;
import com.capstone.steam.security.AppUserPrincipal;
import com.capstone.steam.wishlist.dto.WishlistItemResponse;

@RestController
@RequestMapping("/api/wishlist")
public class WishlistController {

    private final WishlistService wishlistService;

    public WishlistController(WishlistService wishlistService) {
        this.wishlistService = wishlistService;
    }

    @GetMapping
    public PageResponse<WishlistItemResponse> list(@AuthenticationPrincipal AppUserPrincipal user, Pageable pageable) {
        return wishlistService.list(user.getId(), pageable);
    }

    @PutMapping("/{gameId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void add(@AuthenticationPrincipal AppUserPrincipal user, @PathVariable Long gameId) {
        wishlistService.add(user.getId(), gameId);
    }

    @DeleteMapping("/{gameId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void remove(@AuthenticationPrincipal AppUserPrincipal user, @PathVariable Long gameId) {
        wishlistService.remove(user.getId(), gameId);
    }
}
