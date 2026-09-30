package com.capstone.steam.game;

public enum GameStatus {
    /** Visible solo para su developer. */
    DRAFT,
    /** Visible en el catálogo y comprable. */
    PUBLISHED,
    /** Retirado de la tienda por un admin; sigue en las bibliotecas de quien lo compró. */
    DELISTED
}
