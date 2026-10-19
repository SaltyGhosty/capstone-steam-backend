package com.capstone.steam.cover;

import java.io.IOException;
import java.io.UncheckedIOException;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartFile;

import com.capstone.steam.common.exception.BadRequestException;
import com.capstone.steam.game.GameManagementService;
import com.capstone.steam.game.dto.GameDetailResponse;

/**
 * Sube la portada de un juego y la guarda en {@code coverUrl}. Se valida todo antes de llamar al almacén
 * (dueño, tamaño y tipo real por bytes). No es transaccional: no se retiene una transacción durante la subida.
 */
@Service
public class CoverService {

    /** Tamaño máximo de una portada (el mismo límite que {@code spring.servlet.multipart.max-file-size}). */
    static final long MAX_BYTES = 5L * 1024 * 1024;

    private final CoverStorage storage;
    private final GameManagementService games;

    public CoverService(CoverStorage storage, GameManagementService games) {
        this.storage = storage;
        this.games = games;
    }

    public boolean enabled() {
        return storage.enabled();
    }

    /** El developer sube la portada de un juego suyo (404 si no existe, 403 si es de otro). */
    public GameDetailResponse uploadAsDeveloper(Long developerId, Long gameId, MultipartFile file) {
        games.getOwn(developerId, gameId); // comprueba la propiedad antes de tocar el almacén
        String url = store(gameId, file);
        return games.setCover(developerId, gameId, url);
    }

    /** El admin sube la portada de cualquier juego. */
    public GameDetailResponse uploadAsAdmin(Long gameId, MultipartFile file) {
        games.getAny(gameId);
        String url = store(gameId, file);
        return games.setCoverAsAdmin(gameId, url);
    }

    private String store(Long gameId, MultipartFile file) {
        if (file.getSize() > MAX_BYTES) {
            throw new MaxUploadSizeExceededException(MAX_BYTES);
        }
        if (!storage.enabled()) {
            throw new CoverDisabledException();
        }
        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        ImageType type = ImageType.detect(bytes).orElseThrow(() -> new BadRequestException("cover.invalidType"));
        return storage.upload(gameId, bytes, type);
    }
}
