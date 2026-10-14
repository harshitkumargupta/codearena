package com.codearena.api.submission;

import com.codearena.api.contest.ContestSubmissionValidator;
import com.codearena.api.error.ForbiddenException;
import com.codearena.api.error.NotFoundException;
import com.codearena.api.error.ValidationException;
import com.codearena.api.judging.SubmissionCreatedEvent;
import com.codearena.api.problem.Problem;
import com.codearena.api.problem.ProblemRepository;
import com.codearena.api.security.AuthUser;
import com.codearena.api.submission.dto.SubmissionResponse;
import com.codearena.api.submission.dto.SubmissionSummary;
import com.codearena.api.submission.dto.SubmitRequest;
import com.codearena.api.submission.dto.SubmitResponse;
import com.codearena.api.web.PageRequests;
import com.codearena.api.web.PageResponse;
import java.time.Clock;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SubmissionService {

    private final SubmissionRepository submissionRepository;
    private final ProblemRepository problemRepository;
    private final ContestSubmissionValidator contestValidator;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;

    public SubmissionService(SubmissionRepository submissionRepository, ProblemRepository problemRepository,
                             ContestSubmissionValidator contestValidator, ApplicationEventPublisher eventPublisher,
                             Clock clock) {
        this.submissionRepository = submissionRepository;
        this.problemRepository = problemRepository;
        this.contestValidator = contestValidator;
        this.eventPublisher = eventPublisher;
        this.clock = clock;
    }

    @Transactional
    public SubmitResponse submit(AuthUser user, SubmitRequest request) {
        Problem problem = problemRepository.findById(request.problemId())
                .filter(Problem::isPublished)
                .orElseThrow(() -> new ValidationException("problemId", "problem does not exist"));
        if (request.contestId() != null) {
            contestValidator.validate(user, request.contestId(), problem.getId());
        }
        Submission submission = submissionRepository.save(new Submission(user.id(), problem.getId(),
                request.contestId(), request.language(), request.sourceCode(), clock.instant()));
        eventPublisher.publishEvent(new SubmissionCreatedEvent(submission.getId(), submission.getLanguage()));
        return new SubmitResponse(submission.getId(), submission.getStatus());
    }

    @Transactional(readOnly = true)
    public SubmissionResponse get(AuthUser user, long id) {
        Submission submission = submissionRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Submission not found"));
        if (!user.isAdmin() && submission.getUserId() != user.id()) {
            throw new ForbiddenException("Access denied");
        }
        return SubmissionResponse.from(submission);
    }

    @Transactional(readOnly = true)
    public PageResponse<SubmissionSummary> mine(AuthUser user, Long problemId, int page, int size) {
        PageRequest pageable = PageRequests.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<Submission> result = problemId == null
                ? submissionRepository.findByUserId(user.id(), pageable)
                : submissionRepository.findByUserIdAndProblemId(user.id(), problemId, pageable);
        return PageResponse.of(result, SubmissionSummary::from);
    }
}
