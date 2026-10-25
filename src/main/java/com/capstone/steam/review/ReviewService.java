package com.capstone.steam.review;

import java.util.Set;

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
import com.capstone.steam.game.GameVisibility;
import com.capstone.steam.library.LibraryRepository;
import com.capstone.steam.review.dto.ReviewRequest;
import com.capstone.steam.review.dto.ReviewResponse;
import com.capstone.steam.user.User;
import com.capstone.steam.user.UserRepository;

/**
 * Recensioni. I contatori del gioco (recensioni e positive) si mantengono con
 * {@link GameRepository#applyReviewDelta} nella stessa transazione: se la recensione fallisce (es.
 * duplicata), il delta fa rollback con essa.
 *
 * <p>applyReviewDelta svuota il contesto di persistenza (clearAutomatically), quindi la
 * risposta si costruisce PRIMA di chiamarlo, mentre le entità sono ancora gestite.
 */
@Service
public class ReviewService {

    private static final Set<String> LANGUAGES = Set.of("es", "en", "it");
    private static final Sort NEWEST_FIRST = Sort.by(Sort.Direction.DESC, "createdAt");

    private final ReviewRepository reviewRepository;
    private final GameRepository gameRepository;
    private final LibraryRepository libraryRepository;
    private final UserRepository userRepository;
    private final GameVisibility gameVisibility;

    public ReviewService(ReviewRepository reviewRepository, GameRepository gameRepository,
            LibraryRepository libraryRepository, UserRepository userRepository, GameVisibility gameVisibility) {
        this.reviewRepository = reviewRepository;
        this.gameRepository = gameRepository;
        this.libraryRepository = libraryRepository;
        this.userRepository = userRepository;
        this.gameVisibility = gameVisibility;
    }

    @Transactional(readOnly = true)
    public PageResponse<ReviewResponse> listForGame(String slug, String language, Pageable pageable, Long viewerId,
            boolean viewerIsAdmin) {
        if (language != null && !LANGUAGES.contains(language)) {
            throw new BadRequestException("review.invalidLanguage", language);
        }
        Game game = gameRepository.findBySlug(slug)
                .filter(found -> gameVisibility.canView(found, viewerId, viewerIsAdmin))
                .orElseThrow(() -> NotFoundException.of("game", slug));
        PageRequest paging = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), NEWEST_FIRST);
        var page = language == null
                ? reviewRepository.findByGameId(game.getId(), paging)
                : reviewRepository.findByGameIdAndLanguage(game.getId(), language, paging);
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

    /** L'autore o un admin (moderazione). */
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

    /** Blocca la riga: due update/delete simultanei non calcolano il delta con un voto vecchio. */
    private Review findReview(Long reviewId) {
        return reviewRepository.findByIdForUpdate(reviewId).orElseThrow(() -> NotFoundException.of("review", reviewId));
    }

    private static String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
