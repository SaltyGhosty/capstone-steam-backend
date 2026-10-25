package com.capstone.steam.recommendation;

/** OpenRouter ha risposto 429 (senza quota): per il client resta un 503, ma attiva il cortocircuito. */
public class AssistantRateLimitedException extends AssistantUnavailableException {

    public AssistantRateLimitedException(Throwable cause) {
        super(cause);
    }
}
