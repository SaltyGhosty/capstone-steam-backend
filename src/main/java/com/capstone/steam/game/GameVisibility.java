package com.capstone.steam.game;

import org.springframework.stereotype.Component;

import com.capstone.steam.library.LibraryRepository;

/**
 * Quién puede abrir la ficha de un juego. PUBLISHED, cualquiera; DELISTED (como en Steam), solo el admin, su developer
 * y quien lo tiene en la biblioteca; DRAFT, nadie por los endpoints públicos (el developer lo gestiona en su panel).
 */
@Component
public class GameVisibility {

    private final LibraryRepository libraryRepository;

    public GameVisibility(LibraryRepository libraryRepository) {
        this.libraryRepository = libraryRepository;
    }

    /** {@code viewerId} es null sin sesión. */
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
