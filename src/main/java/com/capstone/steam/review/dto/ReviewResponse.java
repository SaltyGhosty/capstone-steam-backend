package com.capstone.steam.review.dto;

import java.time.Instant;

import com.capstone.steam.review.Review;

public record ReviewResponse(
        Long id,
        Long gameId,
        int rating,
        boolean recommended,
        String body,
        Author author,
        Instant createdAt,
        Instant updatedAt) {

    public record Author(Long id, String displayName) {
    }

    public static ReviewResponse from(Review review) {
        return new ReviewResponse(review.getId(), review.getGame().getId(), review.getRating(), review.isRecommended(),
                review.getBody(), new Author(review.getUser().getId(), review.getUser().getDisplayName()),
                review.getCreatedAt(), review.getUpdatedAt());
    }
}
