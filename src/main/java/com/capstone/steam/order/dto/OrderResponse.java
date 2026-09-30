package com.capstone.steam.order.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import com.capstone.steam.order.Order;
import com.capstone.steam.order.OrderItem;
import com.capstone.steam.order.OrderStatus;
import com.capstone.steam.order.Payment;
import com.capstone.steam.order.PaymentMethod;
import com.capstone.steam.order.PaymentStatus;

/** {@code lastPayment} es null mientras el pedido no se ha intentado pagar. */
public record OrderResponse(
        Long id,
        OrderStatus status,
        BigDecimal total,
        Instant createdAt,
        Instant paidAt,
        List<Item> items,
        PaymentInfo lastPayment) {

    public record Item(Long gameId, String title, String slug, String coverUrl, BigDecimal unitPrice) {

        static Item from(OrderItem item) {
            return new Item(item.getGame().getId(), item.getGame().getTitle(), item.getGame().getSlug(),
                    item.getGame().getCoverUrl(), item.getUnitPrice());
        }
    }

    public record PaymentInfo(PaymentMethod method, PaymentStatus status, String reference, String failureReason,
            Instant createdAt) {

        static PaymentInfo from(Payment payment) {
            return new PaymentInfo(payment.getMethod(), payment.getStatus(), payment.getProviderRef(),
                    payment.getFailureReason(), payment.getCreatedAt());
        }
    }

    public static OrderResponse from(Order order, Payment lastPayment) {
        return new OrderResponse(order.getId(), order.getStatus(), order.getTotal(), order.getCreatedAt(),
                order.getPaidAt(), order.getItems().stream().map(Item::from).toList(),
                lastPayment == null ? null : PaymentInfo.from(lastPayment));
    }
}
