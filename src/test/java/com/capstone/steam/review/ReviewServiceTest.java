package com.capstone.steam.review;

import static org.assertj.core.api.Assertions.assertThat;
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
import org.springframework.data.domain.Pageable;

import com.capstone.steam.common.exception.BadRequestException;
import com.capstone.steam.common.exception.ConflictException;
import com.capstone.steam.common.exception.ForbiddenException;
import com.capstone.steam.game.Game;
import com.capstone.steam.game.GameRepository;
import com.capstone.steam.library.LibraryRepository;
import com.capstone.steam.review.dto.ReviewRequest;
import com.capstone.steam.review.dto.ReviewResponse;
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
    @Mock private com.capstone.steam.game.GameVisibility gameVisibility;
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

        assertThatThrownBy(() -> service.create(AUTHOR_ID, request(true))).isInstanceOf(ForbiddenException.class);
        verify(gameRepository, never()).applyReviewDelta(anyLong(), anyInt(), anyInt());
    }

    @Test
    void createRejectsASecondReviewOfTheSameGame() {
        when(libraryRepository.existsByUserIdAndGameId(AUTHOR_ID, GAME_ID)).thenReturn(true);
        when(reviewRepository.existsByUserIdAndGameId(AUTHOR_ID, GAME_ID)).thenReturn(true);

        assertThatThrownBy(() -> service.create(AUTHOR_ID, request(true))).isInstanceOf(ConflictException.class);
        verify(gameRepository, never()).applyReviewDelta(anyLong(), anyInt(), anyInt());
    }

    @Test
    void createAddsOneReviewAndOnePositiveWhenRecommended() {
        when(libraryRepository.existsByUserIdAndGameId(AUTHOR_ID, GAME_ID)).thenReturn(true);

        service.create(AUTHOR_ID, request(true));

        verify(gameRepository).applyReviewDelta(GAME_ID, 1, 1);
    }

    @Test
    void createNegativeAddsOnlyOneReview() {
        when(libraryRepository.existsByUserIdAndGameId(AUTHOR_ID, GAME_ID)).thenReturn(true);

        service.create(AUTHOR_ID, request(false));

        verify(gameRepository).applyReviewDelta(GAME_ID, 0, 1);
    }

    @Test
    void createWithoutLanguageUsesTheAuthorLocale() {
        when(libraryRepository.existsByUserIdAndGameId(AUTHOR_ID, GAME_ID)).thenReturn(true);
        when(author.getLocale()).thenReturn("it");

        ReviewResponse response = service.create(AUTHOR_ID, new ReviewRequest(GAME_ID, true, "Bello", null));

        assertThat(response.language()).isEqualTo("it");
    }

    @Test
    void updateWithoutChangingTheVoteLeavesTheCountersAlone() {
        when(reviewRepository.findByIdForUpdate(REVIEW_ID)).thenReturn(Optional.of(existingReview(true)));

        service.update(AUTHOR_ID, REVIEW_ID, request(true));

        verify(gameRepository, never()).applyReviewDelta(anyLong(), anyInt(), anyInt());
    }

    @Test
    void updateChangingTheVoteMovesOnePositive() {
        when(reviewRepository.findByIdForUpdate(REVIEW_ID)).thenReturn(Optional.of(existingReview(true)));

        service.update(AUTHOR_ID, REVIEW_ID, request(false));

        verify(gameRepository).applyReviewDelta(GAME_ID, -1, 0);
    }

    @Test
    void updateAndDeleteLockTheReviewRow() {
        when(reviewRepository.findByIdForUpdate(REVIEW_ID)).thenReturn(Optional.of(existingReview(true)));

        service.update(AUTHOR_ID, REVIEW_ID, request(false));
        service.delete(AUTHOR_ID, false, REVIEW_ID);

        verify(reviewRepository, org.mockito.Mockito.times(2)).findByIdForUpdate(REVIEW_ID);
        verify(reviewRepository, never()).findById(anyLong());
    }

    @Test
    void updateByAnotherUserIsForbidden() {
        when(reviewRepository.findByIdForUpdate(REVIEW_ID)).thenReturn(Optional.of(existingReview(true)));

        assertThatThrownBy(() -> service.update(OTHER_ID, REVIEW_ID, request(false))).isInstanceOf(ForbiddenException.class);
    }

    @Test
    void deleteByAdminSubtractsThePositiveAndOneReview() {
        Review review = existingReview(true);
        when(reviewRepository.findByIdForUpdate(REVIEW_ID)).thenReturn(Optional.of(review));

        service.delete(OTHER_ID, true, REVIEW_ID);

        verify(reviewRepository).delete(review);
        verify(gameRepository).applyReviewDelta(GAME_ID, -1, -1);
    }

    @Test
    void deleteOfANegativeReviewOnlySubtractsOneReview() {
        Review review = existingReview(false);
        when(reviewRepository.findByIdForUpdate(REVIEW_ID)).thenReturn(Optional.of(review));

        service.delete(AUTHOR_ID, false, REVIEW_ID);

        verify(gameRepository).applyReviewDelta(GAME_ID, 0, -1);
    }

    @Test
    void listWithAnInvalidLanguageIsRejected() {
        assertThatThrownBy(() -> service.listForGame("celeste", "fr", Pageable.unpaged(), null, false))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void deleteByNonAuthorNonAdminIsForbidden() {
        when(reviewRepository.findByIdForUpdate(REVIEW_ID)).thenReturn(Optional.of(existingReview(true)));

        assertThatThrownBy(() -> service.delete(OTHER_ID, false, REVIEW_ID)).isInstanceOf(ForbiddenException.class);
        verify(reviewRepository, never()).delete(any());
    }

    private Review existingReview(boolean recommended) {
        return new Review(author, game, recommended, "texto", "es");
    }

    private static ReviewRequest request(boolean recommended) {
        return new ReviewRequest(GAME_ID, recommended, "  Muy bueno  ", "es");
    }
}
