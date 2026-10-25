package com.capstone.steam.cover;

/** Dove si salvano le copertine caricate come file. */
public interface CoverStorage {

    /** False se non ci sono credenziali configurate: il caricamento è disattivato. */
    boolean enabled();

    /**
     * Carica la copertina del gioco (sostituisce la precedente).
     *
     * @return l'URL https pubblico dell'immagine
     * @throws CoverUploadException se lo storage fallisce o restituisce qualcosa di non valido
     */
    String upload(long gameId, byte[] bytes, ImageType type);
}
