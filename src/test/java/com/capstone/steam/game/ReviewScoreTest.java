package com.capstone.steam.game;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class ReviewScoreTest {

    @Test
    void followsSteamRules() {
        assertThat(ReviewScore.of(0, 0)).isEqualTo(ReviewScore.NONE);
        assertThat(ReviewScore.of(9, 9)).isEqualTo(ReviewScore.NONE);
        assertThat(ReviewScore.of(10, 10)).isEqualTo(ReviewScore.POSITIVE);
        assertThat(ReviewScore.of(49, 49)).isEqualTo(ReviewScore.POSITIVE);
        assertThat(ReviewScore.of(40, 50)).isEqualTo(ReviewScore.VERY_POSITIVE);
        assertThat(ReviewScore.of(474, 500)).isEqualTo(ReviewScore.VERY_POSITIVE);
        assertThat(ReviewScore.of(475, 500)).isEqualTo(ReviewScore.OVERWHELMINGLY_POSITIVE);
        assertThat(ReviewScore.of(7, 10)).isEqualTo(ReviewScore.MOSTLY_POSITIVE);
        assertThat(ReviewScore.of(4, 10)).isEqualTo(ReviewScore.MIXED);
        assertThat(ReviewScore.of(2, 10)).isEqualTo(ReviewScore.MOSTLY_NEGATIVE);
        assertThat(ReviewScore.of(1, 10)).isEqualTo(ReviewScore.NEGATIVE);
        assertThat(ReviewScore.of(5, 50)).isEqualTo(ReviewScore.VERY_NEGATIVE);
        assertThat(ReviewScore.of(50, 500)).isEqualTo(ReviewScore.OVERWHELMINGLY_NEGATIVE);
    }

    @Test
    void positivePercentIsFlooredSoItNeverCrossesAThreshold() {
        assertThat(gameWith(35, 44).getPositivePercent()).isEqualTo(79);
        assertThat(gameWith(946, 1000).getPositivePercent()).isEqualTo(94);
        assertThat(gameWith(0, 0).getPositivePercent()).isZero();
    }

    private static Game gameWith(int positive, int total) {
        Game game = new Game();
        ReflectionTestUtils.setField(game, "positiveCount", positive);
        ReflectionTestUtils.setField(game, "reviewCount", total);
        return game;
    }
}
