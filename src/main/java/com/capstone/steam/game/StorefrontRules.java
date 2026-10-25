package com.capstone.steam.game;

import org.springframework.stereotype.Component;

import com.capstone.steam.common.exception.ConflictException;
import com.capstone.steam.common.exception.NotFoundException;
import com.capstone.steam.library.LibraryRepository;

/** Regole condivise da carrello, wishlist e checkout per «si può acquisire questo gioco?». */
@Component
public class StorefrontRules {

    private final GameRepository gameRepository;
    private final LibraryRepository libraryRepository;

    public StorefrontRules(GameRepository gameRepository, LibraryRepository libraryRepository) {
        this.gameRepository = gameRepository;
        this.libraryRepository = libraryRepository;
    }

    /** Gioco pubblicato che l'utente non ha ancora e di cui non è il developer. */
    public Game requireAcquirable(Long userId, Long gameId) {
        Game game = gameRepository.findById(gameId)
                .filter(Game::isPublished)
                .orElseThrow(() -> NotFoundException.of("game", gameId));
        if (game.isOwnedBy(userId)) {
            throw new ConflictException("store.ownGame");
        }
        if (libraryRepository.existsByUserIdAndGameId(userId, gameId)) {
            throw new ConflictException("store.alreadyOwned");
        }
        return game;
    }
}
