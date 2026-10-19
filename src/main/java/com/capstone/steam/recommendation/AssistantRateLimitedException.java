package com.capstone.steam.recommendation;

/** OpenRouter respondió 429 (sin cupo): sigue siendo un 503 para el cliente, pero activa el cortocircuito. */
public class AssistantRateLimitedException extends AssistantUnavailableException {

    public AssistantRateLimitedException(Throwable cause) {
        super(cause);
    }
}
