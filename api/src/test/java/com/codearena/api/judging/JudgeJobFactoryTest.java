package com.codearena.api.judging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.codearena.api.problem.Difficulty;
import com.codearena.api.problem.Problem;
import com.codearena.api.problem.ProblemRepository;
import com.codearena.api.problem.TestCase;
import com.codearena.api.problem.TestCaseRepository;
import com.codearena.api.submission.Submission;
import com.codearena.api.submission.SubmissionRepository;
import com.codearena.common.JudgeJob;
import com.codearena.common.Language;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class JudgeJobFactoryTest {

    private static final Instant NOW = Instant.parse("2026-10-13T10:00:00Z");

    private final SubmissionRepository submissionRepository = mock(SubmissionRepository.class);
    private final ProblemRepository problemRepository = mock(ProblemRepository.class);
    private final TestCaseRepository testCaseRepository = mock(TestCaseRepository.class);
    private final JudgeJobFactory factory =
            new JudgeJobFactory(submissionRepository, problemRepository, testCaseRepository);

    @Test
    void buildsJobWithAllTestCasesAndProblemLimits() {
        Submission submission = withId(new Submission(1L, 5L, null, Language.JAVA, "class Main {}", NOW), 10L);
        Problem problem = new Problem(null, NOW);
        problem.update("two-sum", "Two Sum", "s", Difficulty.EASY, 2000, 256, true);
        withId(problem, 5L);
        when(submissionRepository.findById(10L)).thenReturn(Optional.of(submission));
        when(problemRepository.findById(5L)).thenReturn(Optional.of(problem));
        when(testCaseRepository.findByProblemIdOrderByOrdinalAsc(5L)).thenReturn(List.of(
                new TestCase(5L, "1 2\n", "3", true, 1),
                new TestCase(5L, "hidden\n", "secret", false, 2)));

        JudgeJob job = factory.build(10L).orElseThrow();

        assertThat(job.submissionId()).isEqualTo(10L);
        assertThat(job.problemId()).isEqualTo(5L);
        assertThat(job.language()).isEqualTo(Language.JAVA);
        assertThat(job.sourceCode()).isEqualTo("class Main {}");
        assertThat(job.timeLimitMs()).isEqualTo(2000);
        assertThat(job.memoryLimitMb()).isEqualTo(256);
        assertThat(job.testCases()).containsExactly(
                new JudgeJob.TestCase(1, "1 2\n", "3"),
                new JudgeJob.TestCase(2, "hidden\n", "secret"));
    }

    @Test
    void missingSubmissionGivesEmpty() {
        when(submissionRepository.findById(10L)).thenReturn(Optional.empty());

        assertThat(factory.build(10L)).isEmpty();
    }

    private static <T> T withId(T entity, long id) {
        ReflectionTestUtils.setField(entity, "id", id);
        return entity;
    }
}
