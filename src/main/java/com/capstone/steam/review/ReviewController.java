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
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.capstone.steam.common.dto.PageResponse;
import com.capstone.steam.review.dto.ReviewRequest;
import com.capstone.steam.review.dto.ReviewResponse;
import com.capstone.steam.security.AppUserPrincipal;
import com.capstone.steam.user.RoleName;

import jakarta.validation.Valid;

@RestController
public class ReviewController {

    private final ReviewService reviewService;

    public ReviewController(ReviewService reviewService) {
        this.reviewService = reviewService;
    }

    /** Público (GET /api/games/** es permitAll). */
    @GetMapping("/api/games/{slug}/reviews")
    public PageResponse<ReviewResponse> listForGame(@PathVariable String slug, Pageable pageable) {
        return reviewService.listForGame(slug, pageable);
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
        boolean isAdmin = user.getRoles().contains(RoleName.ROLE_ADMIN.name());
        reviewService.delete(user.getId(), isAdmin, id);
    }
}
