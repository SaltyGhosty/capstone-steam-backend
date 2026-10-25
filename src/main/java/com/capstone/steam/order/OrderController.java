package com.capstone.steam.order;

import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.capstone.steam.common.dto.PageResponse;
import com.capstone.steam.order.dto.OrderResponse;
import com.capstone.steam.order.dto.OrderSummaryResponse;
import com.capstone.steam.order.dto.PayRequest;
import com.capstone.steam.security.AppUserPrincipal;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    /** Checkout, passo 1: crea un ordine PENDING con il contenuto attuale del carrello. */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public OrderResponse checkout(@AuthenticationPrincipal AppUserPrincipal user) {
        return orderService.createFromCart(user.getId());
    }

    /** Checkout, passo 2: 200 se il pagamento è approvato, 402 Payment Required se è rifiutato (l'ordine resta FAILED). */
    @PostMapping("/{id}/pay")
    public ResponseEntity<OrderResponse> pay(@AuthenticationPrincipal AppUserPrincipal user, @PathVariable Long id,
            @Valid @RequestBody PayRequest request) {
        var outcome = orderService.pay(user.getId(), id, request);
        return ResponseEntity.status(outcome.approved() ? HttpStatus.OK : HttpStatus.PAYMENT_REQUIRED)
                .body(outcome.order());
    }

    @PostMapping("/{id}/cancel")
    public OrderResponse cancel(@AuthenticationPrincipal AppUserPrincipal user, @PathVariable Long id) {
        return orderService.cancel(user.getId(), id);
    }

    @GetMapping
    public PageResponse<OrderSummaryResponse> history(@AuthenticationPrincipal AppUserPrincipal user,
            Pageable pageable) {
        return orderService.history(user.getId(), pageable);
    }

    @GetMapping("/{id}")
    public OrderResponse get(@AuthenticationPrincipal AppUserPrincipal user, @PathVariable Long id) {
        return orderService.get(user.getId(), id);
    }
}
