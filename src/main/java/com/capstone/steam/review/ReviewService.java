package com.capstone.steam.review;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.capstone.steam.common.dto.PageResponse;
import com.capstone.steam.common.exception.BadRequestException;
import com.capstone.steam.common.exception.ConflictException;
import com.capstone.steam.common.exception.ForbiddenException;
import com.capstone.steam.common.exception.NotFoundException;
import com.capstone.steam.game.Game;
import com.capstone.steam.game.GameRepository;
import com.capstone.steam.library.LibraryRepository;
import com.capstone.steam.review.dto.ReviewRequest;
import com.capstone.steam.review.dto.ReviewResponse;
import com.capstone.steam.user.UserRepository;

/**
 * Reseñas. La media del juego se mantiene con {@link GameRepository#applyRatingDelta} en la misma
 * transacción: si la reseña falla (p. ej. duplicada), el delta hace rollback con ella.
 *
 * <p>applyRatingDelta limpia el contexto de persistencia (clearAutomatically), así que la
 * respuesta se construye ANTES de llamarlo, mientras las entidades siguen gestionadas.
 */
@Service
public class ReviewService {

    private static final Sort NEWEST_FIRST = Sort.by(Sort.Direction.DESC, "createdAt");

    private final ReviewRepository reviewRepository;
    private final GameRepository gameRepository;
    private final LibraryRepository libraryRepository;
    private final UserRepository userRepository;

    public ReviewService(ReviewRepository reviewRepository, GameRepository gameRepository,
            LibraryRepository libraryRepository, UserRepository userRepository) {
        this.reviewRepository = reviewRepository;
        this.gameRepository = gameRepository;
        this.libraryRepository = libraryRepository;
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public PageResponse<ReviewResponse> listForGame(String slug, Pageable pageable) {
        Game game = gameRepository.findBySlug(slug)
                .filter(Game::isPublished)
                .orElseThrow(() -> NotFoundException.of("game", slug));
        var page = reviewRepository.findByGameId(game.getId(),
                PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), NEWEST_FIRST));
        return PageResponse.of(page, ReviewResponse::from);
    }

    @Transactional
    public ReviewResponse create(Long userId, ReviewRequest request) {
        if (request.gameId() == null) {
            throw new BadRequestException("review.gameRequired");
        }
        Game game = gameRepository.findById(request.gameId())
                .orElseThrow(() -> NotFoundException.of("game", request.gameId()));
        if (!libraryRepository.existsByUserIdAndGameId(userId, game.getId())) {
            throw new ForbiddenException("review.ownersOnly");
        }
        if (reviewRepository.existsByUserIdAndGameId(userId, game.getId())) {
            throw new ConflictException("review.alreadyReviewed");
        }

        Review review = reviewRepository.saveAndFlush(new Review(userRepository.getReferenceById(userId), game,
                request.rating(), request.recommended(), trimToNull(request.body())));
        ReviewResponse response = ReviewResponse.from(review);

        gameRepository.applyRatingDelta(game.getId(), request.rating(), 1);
        return response;
    }

    @Transactional
    public ReviewResponse update(Long userId, Long reviewId, ReviewRequest request) {
        Review review = findReview(reviewId);
        if (!review.getUser().getId().equals(userId)) {
            throw new ForbiddenException("review.authorOnlyEdit");
        }
        int ratingDelta = request.rating() - review.getRating();

        review.setRating(request.rating());
        review.setRecommended(request.recommended());
        review.setBody(trimToNull(request.body()));
        ReviewResponse response = ReviewResponse.from(reviewRepository.saveAndFlush(review));

        if (ratingDelta != 0) {
            gameRepository.applyRatingDelta(review.getGame().getId(), ratingDelta, 0);
        }
        return response;
    }

    /** El autor o un admin (moderación). */
    @Transactional
    public void delete(Long userId, boolean isAdmin, Long reviewId) {
        Review review = findReview(reviewId);
        if (!isAdmin && !review.getUser().getId().equals(userId)) {
            throw new ForbiddenException("review.authorOrAdminDelete");
        }
        Long gameId = review.getGame().getId();
        int rating = review.getRating();
        reviewRepository.delete(review);
        gameRepository.applyRatingDelta(gameId, -rating, -1);
    }

    private Review findReview(Long reviewId) {
        return reviewRepository.findById(reviewId).orElseThrow(() -> NotFoundException.of("review", reviewId));
    }

    private static String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
