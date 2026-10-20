package com.capstone.steam.common.exception;

/** 429 de un límite por ventana: lleva los segundos que faltan para poder reintentar (cabecera {@code Retry-After}). */
public class RateLimitedException extends TooManyRequestsException {

    private final long retryAfterSeconds;

    public RateLimitedException(String code, long retryAfterSeconds) {
        super(code);
        this.retryAfterSeconds = Math.max(1, retryAfterSeconds);
    }

    public long getRetryAfterSeconds() {
        return retryAfterSeconds;
    }
}
