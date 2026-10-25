package com.capstone.steam.common.exception;

/** 429 di un limite a finestra: porta i secondi che mancano per poter riprovare (header {@code Retry-After}). */
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
