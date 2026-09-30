package com.capstone.steam.cart;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.capstone.steam.cart.dto.CartResponse;
import com.capstone.steam.security.AppUserPrincipal;

@RestController
@RequestMapping("/api/cart")
public class CartController {

    private final CartService cartService;

    public CartController(CartService cartService) {
        this.cartService = cartService;
    }

    @GetMapping
    public CartResponse get(@AuthenticationPrincipal AppUserPrincipal user) {
        return cartService.getCart(user.getId());
    }

    /** PUT porque es idempotente: el recurso "juego X en mi carrito" existe o no. */
    @PutMapping("/items/{gameId}")
    public CartResponse add(@AuthenticationPrincipal AppUserPrincipal user, @PathVariable Long gameId) {
        return cartService.addItem(user.getId(), gameId);
    }

    @DeleteMapping("/items/{gameId}")
    public CartResponse remove(@AuthenticationPrincipal AppUserPrincipal user, @PathVariable Long gameId) {
        return cartService.removeItem(user.getId(), gameId);
    }

    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void clear(@AuthenticationPrincipal AppUserPrincipal user) {
        cartService.clear(user.getId());
    }
}
