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
import com.capstone.steam.user.User;
import com.capstone.steam.user.UserRepository;

/**
 * Reseñas. Los contadores del juego (reseñas y positivas) se mantienen con
 * {@link GameRepository#applyReviewDelta} en la misma transacción: si la reseña falla (p. ej.
 * duplicada), el delta hace rollback con ella.
 *
 * <p>applyReviewDelta limpia el contexto de persistencia (clearAutomatically), así que la
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

        User author = userRepository.getReferenceById(userId);
        String language = request.language() != null ? request.language() : author.getLocale();
        Review review = reviewRepository.saveAndFlush(
                new Review(author, game, request.recommended(), trimToNull(request.body()), language));
        ReviewResponse response = ReviewResponse.from(review);

        gameRepository.applyReviewDelta(game.getId(), positive(request.recommended()), 1);
        return response;
    }

    @Transactional
    public ReviewResponse update(Long userId, Long reviewId, ReviewRequest request) {
        Review review = findReview(reviewId);
        if (!review.getUser().getId().equals(userId)) {
            throw new ForbiddenException("review.authorOnlyEdit");
        }
        int positiveDelta = positive(request.recommended()) - positive(review.isRecommended());

        review.setRecommended(request.recommended());
        review.setBody(trimToNull(request.body()));
        if (request.language() != null) {
            review.setLanguage(request.language());
        }
        ReviewResponse response = ReviewResponse.from(reviewRepository.saveAndFlush(review));

        if (positiveDelta != 0) {
            gameRepository.applyReviewDelta(review.getGame().getId(), positiveDelta, 0);
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
        int positive = positive(review.isRecommended());
        reviewRepository.delete(review);
        gameRepository.applyReviewDelta(gameId, -positive, -1);
    }

    private static int positive(boolean recommended) {
        return recommended ? 1 : 0;
    }

    private Review findReview(Long reviewId) {
        return reviewRepository.findById(reviewId).orElseThrow(() -> NotFoundException.of("review", reviewId));
    }

    private static String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
