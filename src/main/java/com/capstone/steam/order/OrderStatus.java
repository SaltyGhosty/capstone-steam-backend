package com.capstone.steam.order;

/** PENDING -> PAID | FAILED | CANCELLED. Da PENDING si esce una sola volta (vedi OrderRepository). */
public enum OrderStatus {
    PENDING,
    PAID,
    FAILED,
    CANCELLED
}
