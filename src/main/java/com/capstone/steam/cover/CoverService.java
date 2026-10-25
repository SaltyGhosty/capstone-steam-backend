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
 * Carica la copertina di un gioco e la salva in {@code coverUrl}. Tutto si valida prima di chiamare lo storage
 * (proprietario, dimensione e tipo reale dai byte). Non è transazionale: non si trattiene una transazione durante il caricamento.
 */
@Service
public class CoverService {

    /** Dimensione massima di una copertina (lo stesso limite di {@code spring.servlet.multipart.max-file-size}). */
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

    /** Il developer carica la copertina di un suo gioco (404 se non esiste, 403 se è di un altro). */
    public GameDetailResponse uploadAsDeveloper(Long developerId, Long gameId, MultipartFile file) {
        games.getOwn(developerId, gameId); // verifica la proprietà prima di toccare lo storage
        String url = store(gameId, file);
        return games.setCover(developerId, gameId, url);
    }

    /** L'admin carica la copertina di qualsiasi gioco. */
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
