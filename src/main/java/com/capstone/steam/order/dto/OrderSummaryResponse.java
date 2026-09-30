package com.capstone.steam.order.dto;

import java.math.BigDecimal;
import java.time.Instant;

import com.capstone.steam.order.Order;
import com.capstone.steam.order.OrderStatus;

/** Fila del historial de pedidos. */
public record OrderSummaryResponse(Long id, OrderStatus status, BigDecimal total, int itemCount, Instant createdAt,
        Instant paidAt) {

    public static OrderSummaryResponse from(Order order) {
        return new OrderSummaryResponse(order.getId(), order.getStatus(), order.getTotal(), order.getItems().size(),
                order.getCreatedAt(), order.getPaidAt());
    }
}
