package com.codearena.api.tutor;

import com.codearena.api.problem.Difficulty;
import com.codearena.api.problem.Problem;
import com.codearena.api.submission.Submission;
import com.codearena.common.Language;
import com.codearena.common.Verdict;
import java.time.Duration;
import java.time.Instant;
import org.springframework.test.util.ReflectionTestUtils;

final class TutorFixtures {

    static final Instant NOW = Instant.parse("2026-10-13T10:00:00Z");
    static final TutorProperties PROPERTIES = new TutorProperties(Duration.ofMinutes(5), 30, 10, 12_000, 8_000);
    static final String HIDDEN_INPUT = "HIDDEN_INPUT_7f3a";
    static final String HIDDEN_EXPECTED = "HIDDEN_EXPECTED_9b2c";

    private TutorFixtures() {
    }

    static Problem problem(long id) {
        Problem problem = new Problem(null, NOW);
        problem.update("two-sum", "Two Sum", "Print the indices of two numbers adding up to the target.",
                Difficulty.EASY, 2000, 256, true);
        ReflectionTestUtils.setField(problem, "id", id);
        return problem;
    }

    static Submission submission(long id, long userId, long problemId, Verdict status, Integer failedOrdinal,
                                 String source) {
        Submission submission = new Submission(userId, problemId, null, Language.PYTHON, source, NOW);
        ReflectionTestUtils.setField(submission, "id", id);
        ReflectionTestUtils.setField(submission, "status", status);
        ReflectionTestUtils.setField(submission, "failedTestOrdinal", failedOrdinal);
        return submission;
    }
}
