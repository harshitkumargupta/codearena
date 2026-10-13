package com.codearena.api.tutor;

import com.codearena.api.error.ConflictException;
import com.codearena.api.error.ForbiddenException;
import com.codearena.api.error.NotFoundException;
import com.codearena.api.error.ValidationException;
import com.codearena.api.problem.Problem;
import com.codearena.api.problem.ProblemRepository;
import com.codearena.api.problem.TestCase;
import com.codearena.api.problem.TestCaseRepository;
import com.codearena.api.security.AuthUser;
import com.codearena.api.submission.Submission;
import com.codearena.api.submission.SubmissionRepository;
import com.codearena.api.tutor.dto.ExplainFailureRequest;
import com.codearena.api.tutor.dto.HintRequest;
import com.codearena.common.Verdict;
import java.util.EnumSet;
import java.util.Set;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class TutorContextLoader {

    static final Set<Verdict> EXPLAINABLE = EnumSet.of(Verdict.WRONG_ANSWER, Verdict.TIME_LIMIT_EXCEEDED,
            Verdict.MEMORY_LIMIT_EXCEEDED, Verdict.RUNTIME_ERROR, Verdict.COMPILATION_ERROR);

    private final ProblemRepository problemRepository;
    private final SubmissionRepository submissionRepository;
    private final TestCaseRepository testCaseRepository;

    public TutorContextLoader(ProblemRepository problemRepository, SubmissionRepository submissionRepository,
                              TestCaseRepository testCaseRepository) {
        this.problemRepository = problemRepository;
        this.submissionRepository = submissionRepository;
        this.testCaseRepository = testCaseRepository;
    }

    @Transactional(readOnly = true)
    public TutorContext forHint(AuthUser user, HintRequest request) {
        Problem problem = problemRepository.findById(request.problemId())
                .filter(Problem::isPublished)
                .orElseThrow(() -> new NotFoundException("Problem not found"));
        Submission submission = null;
        if (request.submissionId() != null) {
            submission = ownedSubmission(user, request.submissionId());
            if (!submission.getProblemId().equals(problem.getId())) {
                throw new ValidationException("submissionId", "submission is for a different problem");
            }
        }
        return build(problem, submission);
    }

    @Transactional(readOnly = true)
    public TutorContext forExplanation(AuthUser user, ExplainFailureRequest request) {
        Submission submission = ownedSubmission(user, request.submissionId());
        if (!EXPLAINABLE.contains(submission.getStatus())) {
            throw new ConflictException("Only a judged failing submission (WRONG_ANSWER, TIME_LIMIT_EXCEEDED, "
                    + "MEMORY_LIMIT_EXCEEDED, RUNTIME_ERROR or COMPILATION_ERROR) can be explained; this one is "
                    + submission.getStatus());
        }
        Problem problem = problemRepository.findById(submission.getProblemId())
                .orElseThrow(() -> new NotFoundException("Problem not found"));
        return build(problem, submission);
    }

    private Submission ownedSubmission(AuthUser user, long submissionId) {
        Submission submission = submissionRepository.findById(submissionId)
                .orElseThrow(() -> new NotFoundException("Submission not found"));
        if (!user.isAdmin() && submission.getUserId() != user.id()) {
            throw new ForbiddenException("Access denied");
        }
        return submission;
    }

    private TutorContext build(Problem problem, Submission submission) {
        TutorContext.SubmissionFacts facts = null;
        TutorContext.SampleCase failingSample = null;
        boolean failedOnHidden = false;
        if (submission != null) {
            facts = new TutorContext.SubmissionFacts(submission.getId(), submission.getLanguage(),
                    submission.getSourceCode(), submission.getStatus(), submission.getFailedTestOrdinal(),
                    submission.getStatus() == Verdict.COMPILATION_ERROR ? submission.getVerdictDetail() : null);
            if (submission.getStatus() == Verdict.WRONG_ANSWER && submission.getFailedTestOrdinal() != null) {
                int failed = submission.getFailedTestOrdinal();
                failingSample = testCaseRepository.findByProblemIdAndSampleTrueOrderByOrdinalAsc(problem.getId())
                        .stream()
                        .filter(TestCase::isSample)
                        .filter(t -> t.getOrdinal() == failed)
                        .findFirst()
                        .map(t -> new TutorContext.SampleCase(t.getOrdinal(), t.getInput(), t.getExpectedOutput()))
                        .orElse(null);
                failedOnHidden = failingSample == null;
            }
        }
        return new TutorContext(problem.getId(), problem.getTitle(), problem.getStatementMd(), facts, failingSample,
                failedOnHidden);
    }
}
