package com.capstone.steam.cart;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.capstone.steam.cart.dto.CartItemResponse;
import com.capstone.steam.cart.dto.CartResponse;
import com.capstone.steam.game.Game;
import com.capstone.steam.game.StorefrontRules;
import com.capstone.steam.user.UserRepository;

@Service
public class CartService {

    private final CartItemRepository cartItemRepository;
    private final UserRepository userRepository;
    private final StorefrontRules storefrontRules;

    public CartService(CartItemRepository cartItemRepository, UserRepository userRepository,
            StorefrontRules storefrontRules) {
        this.cartItemRepository = cartItemRepository;
        this.userRepository = userRepository;
        this.storefrontRules = storefrontRules;
    }

    @Transactional(readOnly = true)
    public CartResponse getCart(Long userId) {
        return CartResponse.of(cartItemRepository.findByUserIdWithGame(userId).stream()
                .map(CartItemResponse::from)
                .toList());
    }

    /** Idempotente: añadir un juego que ya está en el carrito no hace nada. */
    @Transactional
    public CartResponse addItem(Long userId, Long gameId) {
        Game game = storefrontRules.requireAcquirable(userId, gameId);
        if (!cartItemRepository.existsByUserIdAndGameId(userId, gameId)) {
            cartItemRepository.save(new CartItem(userRepository.getReferenceById(userId), game));
        }
        return getCart(userId);
    }

    /** Idempotente: quitar un juego que no está en el carrito no es un error. */
    @Transactional
    public CartResponse removeItem(Long userId, Long gameId) {
        cartItemRepository.deleteByUserIdAndGameId(userId, gameId);
        return getCart(userId);
    }

    @Transactional
    public void clear(Long userId) {
        cartItemRepository.deleteAllByUserId(userId);
    }
}
