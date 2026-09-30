package com.capstone.steam.cart;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.capstone.steam.cart.dto.CartResponse;
import com.capstone.steam.common.exception.ConflictException;
import com.capstone.steam.common.exception.NotFoundException;
import com.capstone.steam.game.Game;
import com.capstone.steam.game.GameRepository;
import com.capstone.steam.game.GameStatus;
import com.capstone.steam.game.StorefrontRules;
import com.capstone.steam.library.LibraryRepository;
import com.capstone.steam.user.User;
import com.capstone.steam.user.UserRepository;

@ExtendWith(MockitoExtension.class)
class CartServiceTest {

    private static final long USER_ID = 1L;
    private static final long DEV_ID = 2L;
    private static final long GAME_ID = 10L;

    @Mock private CartItemRepository cartRepository;
    @Mock private UserRepository userRepository;
    @Mock private GameRepository gameRepository;
    @Mock private LibraryRepository libraryRepository;

    private CartService service() {
        return new CartService(cartRepository, userRepository, new StorefrontRules(gameRepository, libraryRepository));
    }

    @Test
    void cannotAddUnpublishedGame() {
        Game game = game(GameStatus.DRAFT, "10.00", 0);
        when(gameRepository.findById(GAME_ID)).thenReturn(Optional.of(game));

        assertThatThrownBy(() -> service().addItem(USER_ID, GAME_ID)).isInstanceOf(NotFoundException.class);
    }

    @Test
    void developerCannotBuyOwnGame() {
        Game game = game(GameStatus.PUBLISHED, "10.00", 0);
        when(gameRepository.findById(GAME_ID)).thenReturn(Optional.of(game));

        assertThatThrownBy(() -> service().addItem(DEV_ID, GAME_ID)).isInstanceOf(ConflictException.class);
    }

    @Test
    void cannotAddAGameAlreadyOwned() {
        Game game = game(GameStatus.PUBLISHED, "10.00", 0);
        when(gameRepository.findById(GAME_ID)).thenReturn(Optional.of(game));
        when(libraryRepository.existsByUserIdAndGameId(USER_ID, GAME_ID)).thenReturn(true);

        assertThatThrownBy(() -> service().addItem(USER_ID, GAME_ID))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("biblioteca");
    }

    @Test
    void addingTwiceIsIdempotent() {
        Game game = game(GameStatus.PUBLISHED, "10.00", 0);
        when(gameRepository.findById(GAME_ID)).thenReturn(Optional.of(game));
        when(cartRepository.existsByUserIdAndGameId(USER_ID, GAME_ID)).thenReturn(true);

        service().addItem(USER_ID, GAME_ID);

        verify(cartRepository, never()).save(any());
    }

    @Test
    void totalUsesDiscountedPriceAndSkipsUnavailableGames() {
        User user = mock(User.class);
        List<CartItem> items = List.of(
                new CartItem(user, game(GameStatus.PUBLISHED, "20.00", 25)), // 15.00
                new CartItem(user, game(GameStatus.PUBLISHED, "4.99", 0)),   //  4.99
                new CartItem(user, game(GameStatus.DELISTED, "50.00", 0)));  // no cuenta
        when(cartRepository.findByUserIdWithGame(USER_ID)).thenReturn(items);

        CartResponse cart = service().getCart(USER_ID);

        assertThat(cart.itemCount()).isEqualTo(3);
        assertThat(cart.total()).isEqualByComparingTo("19.99");
        assertThat(cart.items()).filteredOn(i -> !i.available()).hasSize(1);
    }

    private static Game game(GameStatus status, String price, int discount) {
        User developer = mock(User.class);
        org.mockito.Mockito.lenient().when(developer.getId()).thenReturn(DEV_ID);
        Game game = new Game("Game", "game", developer, new BigDecimal(price));
        game.setStatus(status);
        game.setDiscountPercent(discount);
        return game;
    }
}
