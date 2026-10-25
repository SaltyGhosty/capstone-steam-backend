package com.capstone.steam.game;

public enum GameStatus {
    /** Visibile solo al suo developer. */
    DRAFT,
    /** Visibile nel catalogo e acquistabile. */
    PUBLISHED,
    /** Ritirato dallo store da un admin; resta nelle librerie di chi lo ha acquistato. */
    DELISTED
}
