package com.capstone.steam.game;

/** Etichetta di valutazione di un gioco, con le regole di Steam. */
public enum ReviewScore {
    OVERWHELMINGLY_POSITIVE,
    VERY_POSITIVE,
    POSITIVE,
    MOSTLY_POSITIVE,
    MIXED,
    MOSTLY_NEGATIVE,
    NEGATIVE,
    VERY_NEGATIVE,
    OVERWHELMINGLY_NEGATIVE,
    NONE;

    /** Recensioni minime perché un gioco abbia un punteggio. */
    public static final int MIN_REVIEWS = 10;

    public static ReviewScore of(int positive, int total) {
        if (total < MIN_REVIEWS) {
            return NONE;
        }
        double percent = 100.0 * positive / total;
        if (percent >= 95 && total >= 500) {
            return OVERWHELMINGLY_POSITIVE;
        }
        if (percent >= 80 && total >= 50) {
            return VERY_POSITIVE;
        }
        if (percent >= 80) {
            return POSITIVE;
        }
        if (percent >= 70) {
            return MOSTLY_POSITIVE;
        }
        if (percent >= 40) {
            return MIXED;
        }
        if (percent >= 20) {
            return MOSTLY_NEGATIVE;
        }
        if (total >= 500) {
            return OVERWHELMINGLY_NEGATIVE;
        }
        if (total >= 50) {
            return VERY_NEGATIVE;
        }
        return NEGATIVE;
    }
}
