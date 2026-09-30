package com.capstone.steam.library;

import java.util.List;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.capstone.steam.cart.CartItemRepository;
import com.capstone.steam.common.dto.PageResponse;
import com.capstone.steam.common.exception.ConflictException;
import com.capstone.steam.common.exception.NotFoundException;
import com.capstone.steam.game.Game;
import com.capstone.steam.game.GameRepository;
import com.capstone.steam.library.dto.GameOwnershipResponse;
import com.capstone.steam.library.dto.LibraryEntryResponse;
import com.capstone.steam.review.ReviewRepository;
import com.capstone.steam.user.User;
import com.capstone.steam.user.UserRepository;
import com.capstone.steam.wishlist.WishlistItemRepository;

@Service
public class LibraryService {

    private static final Sort NEWEST_FIRST = Sort.by(Sort.Direction.DESC, "acquiredAt");

    private final LibraryRepository libraryRepository;
    private final GameRepository gameRepository;
    private final UserRepository userRepository;
    private final CartItemRepository cartItemRepository;
    private final WishlistItemRepository wishlistRepository;
    private final ReviewRepository reviewRepository;

    public LibraryService(LibraryRepository libraryRepository, GameRepository gameRepository,
            UserRepository userRepository, CartItemRepository cartItemRepository,
            WishlistItemRepository wishlistRepository, ReviewRepository reviewRepository) {
        this.libraryRepository = libraryRepository;
        this.gameRepository = gameRepository;
        this.userRepository = userRepository;
        this.cartItemRepository = cartItemRepository;
        this.wishlistRepository = wishlistRepository;
        this.reviewRepository = reviewRepository;
    }

    @Transactional(readOnly = true)
    public PageResponse<LibraryEntryResponse> list(Long userId, Pageable pageable) {
        var page = libraryRepository.findByUserId(userId,
                PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), NEWEST_FIRST));
        return PageResponse.of(page, LibraryEntryResponse::from);
    }

    @Transactional(readOnly = true)
    public GameOwnershipResponse ownership(Long userId, Long gameId) {
        return new GameOwnershipResponse(
                gameId,
                libraryRepository.existsByUserIdAndGameId(userId, gameId),
                cartItemRepository.existsByUserIdAndGameId(userId, gameId),
                wishlistRepository.existsByUserIdAndGameId(userId, gameId),
                reviewRepository.existsByUserIdAndGameId(userId, gameId));
    }

    /** Alta manual (regalo/soporte) por un admin: sin pedido asociado. */
    @Transactional
    public LibraryEntryResponse grant(Long userId, Long gameId) {
        User user = userRepository.findById(userId).orElseThrow(() -> NotFoundException.of("Usuario", userId));
        Game game = gameRepository.findById(gameId).orElseThrow(() -> NotFoundException.of("Juego", gameId));
        if (libraryRepository.existsByUserIdAndGameId(userId, gameId)) {
            throw new ConflictException("El usuario ya tiene este juego");
        }
        LibraryEntry entry = libraryRepository.saveAndFlush(new LibraryEntry(user, game, null));
        // igual que tras una compra: ya no tiene sentido en el carrito ni en la wishlist
        cartItemRepository.deleteByUserIdAndGameId(userId, gameId);
        wishlistRepository.deleteByUserIdAndGameIdIn(userId, List.of(gameId));
        return LibraryEntryResponse.from(entry);
    }
}
