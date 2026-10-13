package com.codearena.api.contest.scoring;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.api.Test;

class UserScoreTest {

    @ParameterizedTest
    @CsvSource({"0, 0", "1, 0", "1, 1", "1, 9999999", "3, 245", "26, 1500000", "12, 0"})
    void encodeDecodeRoundTrip(int solved, long penalty) {
        UserScore score = new UserScore(solved, penalty);

        assertThat(UserScore.decode(score.encode())).isEqualTo(score);
        assertThat(UserScore.decode((long) (double) score.encode())).isEqualTo(score);
    }

    @Test
    void moreSolvedAlwaysRanksHigherThanLessPenalty() {
        assertThat(new UserScore(2, 9_999_999).encode()).isGreaterThan(new UserScore(1, 0).encode());
        assertThat(new UserScore(1, 0).encode()).isGreaterThan(UserScore.ZERO.encode());
    }

    @Test
    void lessPenaltyRanksHigherForSameSolved() {
        assertThat(new UserScore(2, 100).encode()).isGreaterThan(new UserScore(2, 101).encode());
    }

    @Test
    void outOfRangePenaltyIsRejected() {
        assertThatThrownBy(() -> new UserScore(1, UserScore.SOLVED_WEIGHT)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new UserScore(-1, 0)).isInstanceOf(IllegalArgumentException.class);
    }
}
