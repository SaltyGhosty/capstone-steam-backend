package com.capstone.steam.review;

import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.capstone.steam.common.dto.PageResponse;
import com.capstone.steam.review.dto.ReviewRequest;
import com.capstone.steam.review.dto.ReviewResponse;
import com.capstone.steam.security.AppUserPrincipal;

import jakarta.validation.Valid;

@RestController
public class ReviewController {

    private final ReviewService reviewService;

    public ReviewController(ReviewService reviewService) {
        this.reviewService = reviewService;
    }

    /**
     * Público (GET /api/games/** es permitAll), con el principal opcional para las fichas de juegos retirados.
     * {@code language} es opcional: sin él salen todas.
     */
    @GetMapping("/api/games/{slug}/reviews")
    public PageResponse<ReviewResponse> listForGame(@PathVariable String slug,
            @RequestParam(required = false) String language, Pageable pageable,
            @AuthenticationPrincipal AppUserPrincipal user) {
        return reviewService.listForGame(slug, language, pageable, user == null ? null : user.getId(),
                user != null && user.isAdmin());
    }

    @PostMapping("/api/reviews")
    @ResponseStatus(HttpStatus.CREATED)
    public ReviewResponse create(@AuthenticationPrincipal AppUserPrincipal user,
            @Valid @RequestBody ReviewRequest request) {
        return reviewService.create(user.getId(), request);
    }

    @PutMapping("/api/reviews/{id}")
    public ReviewResponse update(@AuthenticationPrincipal AppUserPrincipal user, @PathVariable Long id,
            @Valid @RequestBody ReviewRequest request) {
        return reviewService.update(user.getId(), id, request);
    }

    @DeleteMapping("/api/reviews/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal AppUserPrincipal user, @PathVariable Long id) {
        boolean isAdmin = user.isAdmin();
        reviewService.delete(user.getId(), isAdmin, id);
    }
}
