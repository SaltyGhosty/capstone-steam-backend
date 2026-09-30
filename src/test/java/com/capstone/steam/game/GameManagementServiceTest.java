package com.capstone.steam.game;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.capstone.steam.common.exception.BadRequestException;
import com.capstone.steam.common.exception.ConflictException;
import com.capstone.steam.common.exception.ForbiddenException;
import com.capstone.steam.game.dto.GameRequest;
import com.capstone.steam.genre.GenreService;
import com.capstone.steam.user.User;
import com.capstone.steam.user.UserRepository;

@ExtendWith(MockitoExtension.class)
class GameManagementServiceTest {

    private static final long OWNER_ID = 1L;
    private static final long OTHER_ID = 2L;
    private static final long GAME_ID = 10L;

    @Mock private GameRepository gameRepository;
    @Mock private UserRepository userRepository;
    @Mock private GenreService genreService;
    @InjectMocks private GameManagementService service;

    private Game game;

    @BeforeEach
    void setUp() {
        User owner = mock(User.class);
        when(owner.getId()).thenReturn(OWNER_ID);
        game = new Game("Portal", "portal", owner, new BigDecimal("9.99"));
        when(gameRepository.findWithDetailsById(GAME_ID)).thenReturn(Optional.of(game));
    }

    @Test
    void anotherDeveloperCannotEditTheGame() {
        assertThatThrownBy(() -> service.update(OTHER_ID, GAME_ID, request(null)))
                .isInstanceOf(ForbiddenException.class);
        verify(gameRepository, never()).saveAndFlush(any());
    }

    @Test
    void staleVersionIsRejectedWithConflict() {
        // la entidad sin persistir tiene version null; el cliente envía una versión antigua
        assertThatThrownBy(() -> service.update(OWNER_ID, GAME_ID, request(3L)))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void cannotPublishWithoutGenres() {
        assertThatThrownBy(() -> service.publish(OWNER_ID, GAME_ID))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("género");
    }

    @Test
    void cannotDeleteAPublishedGame() {
        game.setStatus(GameStatus.PUBLISHED);
        assertThatThrownBy(() -> service.delete(OWNER_ID, GAME_ID)).isInstanceOf(ConflictException.class);
        verify(gameRepository, never()).delete(any());
    }

    @Test
    void developerCannotRepublishADelistedGame() {
        game.setStatus(GameStatus.DELISTED);
        assertThatThrownBy(() -> service.publish(OWNER_ID, GAME_ID)).isInstanceOf(ConflictException.class);
    }

    private static GameRequest request(Long version) {
        return new GameRequest("Portal 2", null, new BigDecimal("19.99"), 0, null, null, Set.of(), version);
    }
}
