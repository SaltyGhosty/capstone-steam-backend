package com.capstone.steam.user;

import java.time.Clock;
import java.util.List;
import java.util.UUID;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.capstone.steam.auth.EmailVerificationTokenRepository;
import com.capstone.steam.auth.RefreshTokenRepository;
import com.capstone.steam.cart.CartItemRepository;
import com.capstone.steam.friend.FriendshipRepository;
import com.capstone.steam.game.Game;
import com.capstone.steam.game.GameRepository;
import com.capstone.steam.game.GameStatus;
import com.capstone.steam.notification.event.AccountDeletedEvent;
import com.capstone.steam.review.Review;
import com.capstone.steam.review.ReviewRepository;
import com.capstone.steam.wishlist.WishlistItemRepository;

/**
 * Anonimiza una cuenta cuyo plazo de baja ya venció. Una transacción propia por cuenta (REQUIRES_NEW): si una
 * falla, las demás siguen. Orden de bloqueos: primero la fila del usuario y después sus reseñas y los juegos.
 */
@Service
public class AccountAnonymizer {

    private final UserRepository userRepository;
    private final ReviewRepository reviewRepository;
    private final GameRepository gameRepository;
    private final CartItemRepository cartItemRepository;
    private final WishlistItemRepository wishlistRepository;
    private final FriendshipRepository friendshipRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final EmailVerificationTokenRepository verificationTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    public AccountAnonymizer(UserRepository userRepository, ReviewRepository reviewRepository,
            GameRepository gameRepository, CartItemRepository cartItemRepository,
            WishlistItemRepository wishlistRepository, FriendshipRepository friendshipRepository,
            RefreshTokenRepository refreshTokenRepository,
            EmailVerificationTokenRepository verificationTokenRepository, PasswordEncoder passwordEncoder,
            ApplicationEventPublisher events, Clock clock) {
        this.userRepository = userRepository;
        this.reviewRepository = reviewRepository;
        this.gameRepository = gameRepository;
        this.cartItemRepository = cartItemRepository;
        this.wishlistRepository = wishlistRepository;
        this.friendshipRepository = friendshipRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.verificationTokenRepository = verificationTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.events = events;
        this.clock = clock;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void anonymize(Long userId) {
        // Contexto de persistencia nuevo: el bloqueo relee la fila. Se vuelve a comprobar sobre la fila bloqueada:
        // la cuenta pudo recuperarse o anonimizarse entre que la tarea la listó y ahora.
        User user = userRepository.findByIdForUpdate(userId).orElse(null);
        if (user == null || user.isDeleted() || !user.isPendingDeletion()
                || user.getDeletionScheduledAt().isAfter(clock.instant())) {
            return;
        }
        String email = user.getEmail();
        String displayName = user.getDisplayName();
        String locale = user.getLocale();

        deleteReviews(userId);
        cartItemRepository.deleteAllByUserId(userId);
        wishlistRepository.deleteAllByUserId(userId);
        friendshipRepository.deleteAllOf(userId);
        refreshTokenRepository.deleteAllByUserId(userId);
        verificationTokenRepository.deleteAllByUserId(userId);
        retireStudioGames(userId);

        // Los borrados masivos vacían el contexto: se vuelve a leer la fila (ya bloqueada por esta transacción).
        User fresh = userRepository.findByIdForUpdate(userId).orElseThrow();
        fresh.anonymize(passwordEncoder.encode(UUID.randomUUID().toString()), clock.instant());
        events.publishEvent(new AccountDeletedEvent(email, displayName, locale));
    }

    /** Como {@code ReviewService.delete}: reseña bloqueada, su voto sale del contador del juego y se borra. */
    private void deleteReviews(Long userId) {
        List<Long> reviewIds = reviewRepository.findByUserId(userId).stream().map(Review::getId).toList();
        for (Long reviewId : reviewIds) {
            reviewRepository.findByIdForUpdate(reviewId).ifPresent(review -> {
                Long gameId = review.getGame().getId();
                int positive = review.isRecommended() ? 1 : 0;
                reviewRepository.delete(review);
                gameRepository.applyReviewDelta(gameId, -positive, -1);
            });
        }
    }

    /** PUBLISHED pasa a DELISTED; el DRAFT se borra salvo que algo lo referencie (entonces también DELISTED). */
    private void retireStudioGames(Long userId) {
        for (Game game : gameRepository.findByDeveloperId(userId)) {
            if (game.getStatus() == GameStatus.DRAFT && !gameRepository.isReferenced(game.getId())) {
                gameRepository.delete(game);
            } else {
                game.setStatus(GameStatus.DELISTED);
            }
        }
        gameRepository.flush();
    }
}
