package com.codearena.api.contest.scoring;

import com.codearena.common.Verdict;
import java.time.Duration;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * ICPC scoring (section 8) for one user. Submissions are ordered by submission time, not judging time, so the
 * result does not depend on the order in which verdicts arrive.
 */
public final class ScoreCalculator {

    public static final Set<Verdict> WRONG_ATTEMPTS = EnumSet.of(Verdict.WRONG_ANSWER,
            Verdict.TIME_LIMIT_EXCEEDED, Verdict.MEMORY_LIMIT_EXCEEDED, Verdict.RUNTIME_ERROR);

    private static final Comparator<ScoredSubmission> SUBMISSION_ORDER =
            Comparator.comparing(ScoredSubmission::createdAt).thenComparingLong(ScoredSubmission::id);

    private ScoreCalculator() {
    }

    public static UserScore score(ContestWindow window, int penaltyPerWrongAttempt, List<ScoredSubmission> submissions) {
        Map<Long, List<ScoredSubmission>> byProblem = submissions.stream()
                .filter(s -> window.contains(s.createdAt()))
                .collect(Collectors.groupingBy(ScoredSubmission::problemId));
        int solved = 0;
        long penalty = 0;
        for (List<ScoredSubmission> attempts : byProblem.values()) {
            int wrongAttempts = 0;
            for (ScoredSubmission submission : attempts.stream().sorted(SUBMISSION_ORDER).toList()) {
                if (submission.status() == Verdict.ACCEPTED) {
                    solved++;
                    penalty += Duration.between(window.start(), submission.createdAt()).toMinutes()
                            + (long) penaltyPerWrongAttempt * wrongAttempts;
                    break;
                }
                if (WRONG_ATTEMPTS.contains(submission.status())) {
                    wrongAttempts++;
                }
            }
        }
        return new UserScore(solved, penalty);
    }
}
