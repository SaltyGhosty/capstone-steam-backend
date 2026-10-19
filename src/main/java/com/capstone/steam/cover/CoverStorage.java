package com.capstone.steam.cover;

/** Dónde se guardan las portadas subidas como archivo. */
public interface CoverStorage {

    /** Falso si no hay credenciales configuradas: la subida está desactivada. */
    boolean enabled();

    /**
     * Sube la portada del juego (reemplaza la anterior).
     *
     * @return la URL https pública de la imagen
     * @throws CoverUploadException si el almacén falla o devuelve algo inválido
     */
    String upload(long gameId, byte[] bytes, ImageType type);
}
