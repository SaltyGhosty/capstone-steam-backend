package com.capstone.steam.friend;

import java.util.List;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.capstone.steam.friend.dto.PublicProfileResponse;
import com.capstone.steam.friend.dto.SearchHit;
import com.capstone.steam.security.AppUserPrincipal;

/** Búsqueda de jugadores y perfil público. No choca con {@code /api/users/me}: «me» no es un username válido. */
@RestController
@RequestMapping("/api/users")
public class ProfileController {

    private final FriendService friendService;

    public ProfileController(FriendService friendService) {
        this.friendService = friendService;
    }

    @GetMapping("/search")
    public List<SearchHit> search(@AuthenticationPrincipal AppUserPrincipal principal,
            @RequestParam(required = false) String q) {
        return friendService.search(principal.getId(), q);
    }

    @GetMapping("/{username}/profile")
    public PublicProfileResponse profile(@AuthenticationPrincipal AppUserPrincipal principal,
            @PathVariable String username) {
        return friendService.profile(principal.getId(), username);
    }
}
