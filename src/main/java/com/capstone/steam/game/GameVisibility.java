package com.capstone.steam.game;

import org.springframework.stereotype.Component;

import com.capstone.steam.library.LibraryRepository;

/**
 * Chi può aprire la scheda di un gioco. PUBLISHED, chiunque; DELISTED (come su Steam), solo l'admin, il suo developer
 * e chi lo ha in libreria; DRAFT, nessuno dagli endpoint pubblici (il developer lo gestisce nel suo pannello).
 */
@Component
public class GameVisibility {

    private final LibraryRepository libraryRepository;

    public GameVisibility(LibraryRepository libraryRepository) {
        this.libraryRepository = libraryRepository;
    }

    /** {@code viewerId} è null senza sessione. */
    public boolean canView(Game game, Long viewerId, boolean viewerIsAdmin) {
        return switch (game.getStatus()) {
            case PUBLISHED -> true;
            case DELISTED -> viewerIsAdmin
                    || (viewerId != null && (game.isOwnedBy(viewerId)
                            || libraryRepository.existsByUserIdAndGameId(viewerId, game.getId())));
            case DRAFT -> false;
        };
    }
}
