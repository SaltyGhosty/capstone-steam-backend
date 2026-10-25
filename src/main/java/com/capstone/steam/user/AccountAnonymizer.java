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
import com.capstone.steam.friend.FriendMessageRepository;
import com.capstone.steam.friend.FriendshipRepository;
import com.capstone.steam.game.Game;
import com.capstone.steam.game.GameRepository;
import com.capstone.steam.game.GameStatus;
import com.capstone.steam.notification.event.AccountDeletedEvent;
import com.capstone.steam.review.Review;
import com.capstone.steam.review.ReviewRepository;
import com.capstone.steam.wishlist.WishlistItemRepository;

/**
 * Anonimizza un account il cui periodo di cancellazione è scaduto. Una transazione propria per account (REQUIRES_NEW): se una
 * fallisce, le altre proseguono. Ordine dei lock: prima la riga dell'utente e poi le sue recensioni e i giochi.
 */
@Service
public class AccountAnonymizer {

    private final UserRepository userRepository;
    private final ReviewRepository reviewRepository;
    private final GameRepository gameRepository;
    private final CartItemRepository cartItemRepository;
    private final WishlistItemRepository wishlistRepository;
    private final FriendshipRepository friendshipRepository;
    private final FriendMessageRepository messageRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final EmailVerificationTokenRepository verificationTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    public AccountAnonymizer(UserRepository userRepository, ReviewRepository reviewRepository,
            GameRepository gameRepository, CartItemRepository cartItemRepository,
            WishlistItemRepository wishlistRepository, FriendshipRepository friendshipRepository,
            FriendMessageRepository messageRepository,
            RefreshTokenRepository refreshTokenRepository,
            EmailVerificationTokenRepository verificationTokenRepository, PasswordEncoder passwordEncoder,
            ApplicationEventPublisher events, Clock clock) {
        this.userRepository = userRepository;
        this.reviewRepository = reviewRepository;
        this.gameRepository = gameRepository;
        this.cartItemRepository = cartItemRepository;
        this.wishlistRepository = wishlistRepository;
        this.friendshipRepository = friendshipRepository;
        this.messageRepository = messageRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.verificationTokenRepository = verificationTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.events = events;
        this.clock = clock;
    }

    /** Restituisce se l'ha anonimizzato: {@code false} se non toccava più (recuperato, già eliminato o non ancora scaduto). */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean anonymize(Long userId) {
        // Nuovo contesto di persistenza: il lock rilegge la riga. Si ricontrolla sulla riga bloccata:
        // l'account può essere stato recuperato o anonimizzato tra il momento in cui il task l'ha elencato e adesso.
        User user = userRepository.findByIdForUpdate(userId).orElse(null);
        if (user == null || user.isDeleted() || !user.isPendingDeletion()
                || user.getDeletionScheduledAt().isAfter(clock.instant())) {
            return false;
        }
        String email = user.getEmail();
        String displayName = user.getDisplayName();
        String locale = user.getLocale();

        deleteReviews(userId);
        cartItemRepository.deleteAllByUserId(userId);
        wishlistRepository.deleteAllByUserId(userId);
        friendshipRepository.deleteAllOf(userId);
        messageRepository.deleteAllOf(userId);
        refreshTokenRepository.deleteAllByUserId(userId);
        verificationTokenRepository.deleteAllByUserId(userId);
        retireStudioGames(userId);

        // Le cancellazioni di massa svuotano il contesto: si rilegge la riga (già bloccata da questa transazione).
        User fresh = userRepository.findByIdForUpdate(userId).orElseThrow();
        fresh.anonymize(passwordEncoder.encode(UUID.randomUUID().toString()), clock.instant());
        events.publishEvent(new AccountDeletedEvent(email, displayName, locale));
        return true;
    }

    /** Come {@code ReviewService.delete}: recensione bloccata, il suo voto esce dal contatore del gioco e viene cancellata. */
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

    /** PUBLISHED passa a DELISTED; il DRAFT si cancella a meno che qualcosa lo referenzi (allora anche DELISTED). */
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
