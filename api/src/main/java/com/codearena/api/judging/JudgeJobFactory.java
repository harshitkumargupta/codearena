package com.codearena.api.judging;

import com.codearena.api.problem.Problem;
import com.codearena.api.problem.ProblemRepository;
import com.codearena.api.problem.TestCaseRepository;
import com.codearena.api.submission.Submission;
import com.codearena.api.submission.SubmissionRepository;
import com.codearena.common.JudgeJob;
import java.util.Optional;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class JudgeJobFactory {

    private final SubmissionRepository submissionRepository;
    private final ProblemRepository problemRepository;
    private final TestCaseRepository testCaseRepository;

    public JudgeJobFactory(SubmissionRepository submissionRepository, ProblemRepository problemRepository,
                           TestCaseRepository testCaseRepository) {
        this.submissionRepository = submissionRepository;
        this.problemRepository = problemRepository;
        this.testCaseRepository = testCaseRepository;
    }

    @Transactional(readOnly = true)
    public Optional<JudgeJob> build(long submissionId) {
        return submissionRepository.findById(submissionId).flatMap(submission ->
                problemRepository.findById(submission.getProblemId()).map(problem -> toJob(submission, problem)));
    }

    private JudgeJob toJob(Submission submission, Problem problem) {
        return new JudgeJob(submission.getId(), problem.getId(), submission.getLanguage(), submission.getSourceCode(),
                problem.getTimeLimitMs(), problem.getMemoryLimitMb(),
                testCaseRepository.findByProblemIdOrderByOrdinalAsc(problem.getId()).stream()
                        .map(t -> new JudgeJob.TestCase(t.getOrdinal(), t.getInput(), t.getExpectedOutput()))
                        .toList());
    }
}
