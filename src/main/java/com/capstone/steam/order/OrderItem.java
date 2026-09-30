package com.capstone.steam.order;

import java.math.BigDecimal;

import com.capstone.steam.game.Game;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "order_items")
public class OrderItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "game_id", nullable = false)
    private Game game;

    /** Precio pagado (con descuento) en el momento de la compra. */
    @Column(name = "unit_price", nullable = false, precision = 10, scale = 2)
    private BigDecimal unitPrice;

    protected OrderItem() {
    }

    OrderItem(Order order, Game game, BigDecimal unitPrice) {
        this.order = order;
        this.game = game;
        this.unitPrice = unitPrice;
    }

    public Long getId() {
        return id;
    }

    public Order getOrder() {
        return order;
    }

    public Game getGame() {
        return game;
    }

    public BigDecimal getUnitPrice() {
        return unitPrice;
    }
}
