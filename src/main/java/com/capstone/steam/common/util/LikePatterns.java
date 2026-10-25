package com.capstone.steam.common.util;

/** Aiuti per query LIKE con testo dell'utente. */
public final class LikePatterns {

    private LikePatterns() {
    }

    /**
     * Pattern "contiene" per {@code like ... escape '!'}: fa l'escape di % e _ con "!" perché il testo dell'utente
     * non agisca mai da carattere jolly.
     */
    public static String contains(String term) {
        String escaped = term.replace("!", "!!").replace("%", "!%").replace("_", "!_");
        return "%" + escaped + "%";
    }
}
