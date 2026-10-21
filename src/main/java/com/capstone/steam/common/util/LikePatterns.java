package com.capstone.steam.common.util;

/** Ayudas para consultas LIKE con texto del usuario. */
public final class LikePatterns {

    private LikePatterns() {
    }

    /**
     * Patron "contiene" para {@code like ... escape '!'}: escapa % y _ con "!" para que el texto del usuario
     * nunca actue como comodin.
     */
    public static String contains(String term) {
        String escaped = term.replace("!", "!!").replace("%", "!%").replace("_", "!_");
        return "%" + escaped + "%";
    }
}
