package com.capstone.steam.order;

/** PENDING -> PAID | FAILED | CANCELLED. Solo se sale de PENDING una vez (ver OrderRepository). */
public enum OrderStatus {
    PENDING,
    PAID,
    FAILED,
    CANCELLED
}
