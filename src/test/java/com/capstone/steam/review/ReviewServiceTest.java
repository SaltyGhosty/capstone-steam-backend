package com.capstone.steam.review;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import com.capstone.steam.common.exception.ConflictException;
import com.capstone.steam.common.exception.ForbiddenException;
import com.capstone.steam.game.Game;
import com.capstone.steam.game.GameRepository;
import com.capstone.steam.library.LibraryRepository;
import com.capstone.steam.review.dto.ReviewRequest;
import com.capstone.steam.user.User;
import com.capstone.steam.user.UserRepository;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ReviewServiceTest {

    private static final long AUTHOR_ID = 1L;
    private static final long OTHER_ID = 2L;
    private static final long GAME_ID = 10L;
    private static final long REVIEW_ID = 100L;

    @Mock private ReviewRepository reviewRepository;
    @Mock private GameRepository gameRepository;
    @Mock private LibraryRepository libraryRepository;
    @Mock private UserRepository userRepository;
    @InjectMocks private ReviewService service;

    private Game game;
    private User author;

    @BeforeEach
    void setUp() {
        author = mock(User.class);
        when(author.getId()).thenReturn(AUTHOR_ID);
        game = mock(Game.class);
        when(game.getId()).thenReturn(GAME_ID);
        when(gameRepository.findById(GAME_ID)).thenReturn(Optional.of(game));
        when(userRepository.getReferenceById(AUTHOR_ID)).thenReturn(author);
        when(reviewRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void createRequiresTheGameInTheLibrary() {
        when(libraryRepository.existsByUserIdAndGameId(AUTHOR_ID, GAME_ID)).thenReturn(false);

        assertThatThrownBy(() -> service.create(AUTHOR_ID, request(5))).isInstanceOf(ForbiddenException.class);
        verify(gameRepository, never()).applyRatingDelta(anyLong(), anyLong(), anyInt());
    }

    @Test
    void createRejectsASecondReviewOfTheSameGame() {
        when(libraryRepository.existsByUserIdAndGameId(AUTHOR_ID, GAME_ID)).thenReturn(true);
        when(reviewRepository.existsByUserIdAndGameId(AUTHOR_ID, GAME_ID)).thenReturn(true);

        assertThatThrownBy(() -> service.create(AUTHOR_ID, request(5))).isInstanceOf(ConflictException.class);
        verify(gameRepository, never()).applyRatingDelta(anyLong(), anyLong(), anyInt());
    }

    @Test
    void createAddsRatingAndOneToTheCounters() {
        when(libraryRepository.existsByUserIdAndGameId(AUTHOR_ID, GAME_ID)).thenReturn(true);

        service.create(AUTHOR_ID, request(4));

        verify(gameRepository).applyRatingDelta(GAME_ID, 4, 1);
    }

    @Test
    void updateAppliesOnlyTheDifference() {
        when(reviewRepository.findById(REVIEW_ID)).thenReturn(Optional.of(existingReview(2)));

        service.update(AUTHOR_ID, REVIEW_ID, request(5));

        verify(gameRepository).applyRatingDelta(GAME_ID, 3, 0);
    }

    @Test
    void updateByAnotherUserIsForbidden() {
        when(reviewRepository.findById(REVIEW_ID)).thenReturn(Optional.of(existingReview(2)));

        assertThatThrownBy(() -> service.update(OTHER_ID, REVIEW_ID, request(1))).isInstanceOf(ForbiddenException.class);
    }

    @Test
    void deleteByAdminSubtractsRatingAndOne() {
        Review review = existingReview(3);
        when(reviewRepository.findById(REVIEW_ID)).thenReturn(Optional.of(review));

        service.delete(OTHER_ID, true, REVIEW_ID);

        verify(reviewRepository).delete(review);
        verify(gameRepository).applyRatingDelta(GAME_ID, -3, -1);
    }

    @Test
    void deleteByNonAuthorNonAdminIsForbidden() {
        when(reviewRepository.findById(REVIEW_ID)).thenReturn(Optional.of(existingReview(3)));

        assertThatThrownBy(() -> service.delete(OTHER_ID, false, REVIEW_ID)).isInstanceOf(ForbiddenException.class);
        verify(reviewRepository, never()).delete(any());
    }

    private Review existingReview(int rating) {
        return new Review(author, game, rating, true, "texto");
    }

    private static ReviewRequest request(int rating) {
        return new ReviewRequest(GAME_ID, rating, true, "  Muy bueno  ");
    }
}
