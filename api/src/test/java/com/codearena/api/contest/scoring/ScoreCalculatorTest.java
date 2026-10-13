package com.codearena.api.contest.scoring;

import static com.codearena.common.Verdict.ACCEPTED;
import static com.codearena.common.Verdict.COMPILATION_ERROR;
import static com.codearena.common.Verdict.INTERNAL_ERROR;
import static com.codearena.common.Verdict.MEMORY_LIMIT_EXCEEDED;
import static com.codearena.common.Verdict.PENDING;
import static com.codearena.common.Verdict.RUNNING;
import static com.codearena.common.Verdict.RUNTIME_ERROR;
import static com.codearena.common.Verdict.TIME_LIMIT_EXCEEDED;
import static com.codearena.common.Verdict.WRONG_ANSWER;
import static org.assertj.core.api.Assertions.assertThat;

import com.codearena.common.Verdict;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;

class ScoreCalculatorTest {

    private static final Instant START = Instant.parse("2026-10-13T10:00:00Z");
    private static final ContestWindow WINDOW = new ContestWindow(START, START.plus(Duration.ofHours(2)));
    private static final int PENALTY = 20;
    private static final long A = 1L;
    private static final long B = 2L;
    private static final long C = 3L;

    private long nextId = 1;

    @Test
    void noSubmissionsScoresZero() {
        assertThat(score()).isEqualTo(UserScore.ZERO);
    }

    @Test
    void acceptedOnFirstTryCostsWholeMinutesSinceStart() {
        assertThat(score(sub(A, ACCEPTED, "00:12:59"))).isEqualTo(new UserScore(1, 12));
    }

    @Test
    void wrongAttemptsBeforeAcceptedAddPenaltyEach() {
        UserScore score = score(sub(A, WRONG_ANSWER, "00:05:00"), sub(A, TIME_LIMIT_EXCEEDED, "00:10:00"),
                sub(A, ACCEPTED, "00:30:00"));

        assertThat(score).isEqualTo(new UserScore(1, 30 + 2 * PENALTY));
    }

    @Test
    void submissionsAfterFirstAcceptedAreIgnored() {
        UserScore score = score(sub(A, ACCEPTED, "00:10:00"), sub(A, WRONG_ANSWER, "00:20:00"),
                sub(A, ACCEPTED, "00:25:00"));

        assertThat(score).isEqualTo(new UserScore(1, 10));
    }

    @Test
    void wrongAttemptsOnUnsolvedProblemAddNothing() {
        UserScore score = score(sub(A, ACCEPTED, "00:10:00"), sub(B, WRONG_ANSWER, "00:20:00"),
                sub(B, RUNTIME_ERROR, "00:40:00"));

        assertThat(score).isEqualTo(new UserScore(1, 10));
    }

    @Test
    void compileAndInternalErrorsAndOpenSubmissionsAreNotPenalized() {
        UserScore score = score(sub(A, COMPILATION_ERROR, "00:01:00"), sub(A, INTERNAL_ERROR, "00:02:00"),
                sub(A, PENDING, "00:03:00"), sub(A, RUNNING, "00:04:00"), sub(A, ACCEPTED, "00:15:00"));

        assertThat(score).isEqualTo(new UserScore(1, 15));
    }

    @Test
    void everyWrongVerdictTypeCounts() {
        UserScore score = score(sub(A, WRONG_ANSWER, "00:01:00"), sub(A, TIME_LIMIT_EXCEEDED, "00:02:00"),
                sub(A, MEMORY_LIMIT_EXCEEDED, "00:03:00"), sub(A, RUNTIME_ERROR, "00:04:00"),
                sub(A, ACCEPTED, "00:05:00"));

        assertThat(score).isEqualTo(new UserScore(1, 5 + 4 * PENALTY));
    }

    @Test
    void submissionsOutsideWindowAreIgnored() {
        ScoredSubmission beforeStart = new ScoredSubmission(nextId++, 1L, A, ACCEPTED, START.minusSeconds(1));
        ScoredSubmission atEnd = new ScoredSubmission(nextId++, 1L, B, ACCEPTED, WINDOW.end());
        ScoredSubmission wrongBeforeStart = new ScoredSubmission(nextId++, 1L, C, WRONG_ANSWER, START.minusSeconds(60));

        UserScore score = score(beforeStart, atEnd, wrongBeforeStart, sub(C, ACCEPTED, "00:00:00"));

        assertThat(score).isEqualTo(new UserScore(1, 0));
    }

    @Test
    void severalProblemsSumTheirPenalties() {
        UserScore score = score(sub(A, ACCEPTED, "00:10:00"), sub(B, WRONG_ANSWER, "00:20:00"),
                sub(B, ACCEPTED, "00:35:00"), sub(C, COMPILATION_ERROR, "01:00:00"), sub(C, ACCEPTED, "01:59:59"));

        assertThat(score).isEqualTo(new UserScore(3, 10 + (35 + PENALTY) + 119));
    }

    @Test
    void judgingOrderDoesNotMatter() {
        List<ScoredSubmission> submissions = new ArrayList<>(List.of(sub(A, WRONG_ANSWER, "00:05:00"),
                sub(A, ACCEPTED, "00:09:00"), sub(A, WRONG_ANSWER, "00:12:00"), sub(B, RUNTIME_ERROR, "00:15:00"),
                sub(B, WRONG_ANSWER, "00:16:00"), sub(B, ACCEPTED, "00:40:00"), sub(C, WRONG_ANSWER, "00:50:00")));
        UserScore expected = new UserScore(2, (9 + PENALTY) + (40 + 2 * PENALTY));
        Random random = new Random(42);

        for (int i = 0; i < 20; i++) {
            Collections.shuffle(submissions, random);
            assertThat(score(submissions.toArray(ScoredSubmission[]::new))).isEqualTo(expected);
        }
    }

    @Test
    void sameSecondIsOrderedBySubmissionId() {
        Instant t = START.plusSeconds(600);
        ScoredSubmission wrongFirst = new ScoredSubmission(10, 1L, A, WRONG_ANSWER, t);
        ScoredSubmission acceptedSecond = new ScoredSubmission(11, 1L, A, ACCEPTED, t);

        assertThat(score(acceptedSecond, wrongFirst)).isEqualTo(new UserScore(1, 10 + PENALTY));
    }

    @Test
    void tiedUsersGetEqualEncodedScores() {
        UserScore fast = score(sub(A, ACCEPTED, "00:30:00"));
        UserScore slowButCleanEnough = score(sub(B, WRONG_ANSWER, "00:01:00"), sub(B, ACCEPTED, "00:10:00"));

        assertThat(fast.encode()).isEqualTo(slowButCleanEnough.encode());
    }

    private UserScore score(ScoredSubmission... submissions) {
        return ScoreCalculator.score(WINDOW, PENALTY, List.of(submissions));
    }

    private ScoredSubmission sub(long problemId, Verdict status, String sinceStart) {
        String[] hms = sinceStart.split(":");
        Instant at = START.plus(Duration.ofHours(Long.parseLong(hms[0])).plusMinutes(Long.parseLong(hms[1]))
                .plusSeconds(Long.parseLong(hms[2])));
        return new ScoredSubmission(nextId++, 1L, problemId, status, at);
    }
}
