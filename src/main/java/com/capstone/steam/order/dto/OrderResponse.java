package com.capstone.steam.order.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import com.capstone.steam.common.i18n.Messages;
import com.capstone.steam.order.Order;
import com.capstone.steam.order.OrderItem;
import com.capstone.steam.order.OrderStatus;
import com.capstone.steam.order.Payment;
import com.capstone.steam.order.PaymentMethod;
import com.capstone.steam.order.PaymentStatus;

/** {@code lastPayment} è null finché non si è tentato di pagare l'ordine. */
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

    /** {@code failureReason} si salva come chiave (payment.declined.*) e esce tradotto nella lingua della richiesta. */
    public record PaymentInfo(PaymentMethod method, PaymentStatus status, String reference, String failureReason,
            Instant createdAt) {

        static PaymentInfo from(Payment payment) {
            return new PaymentInfo(payment.getMethod(), payment.getStatus(), payment.getProviderRef(),
                    Messages.getOrSelf(Messages.currentLocale(), payment.getFailureReason()), payment.getCreatedAt());
        }
    }

    public static OrderResponse from(Order order, Payment lastPayment) {
        return new OrderResponse(order.getId(), order.getStatus(), order.getTotal(), order.getCreatedAt(),
                order.getPaidAt(), order.getItems().stream().map(Item::from).toList(),
                lastPayment == null ? null : PaymentInfo.from(lastPayment));
    }
}
