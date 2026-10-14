package com.codearena.api.judging;

import com.codearena.api.submission.SubmissionRepository;
import com.codearena.common.JudgeQueues;
import com.codearena.common.JudgeResult;
import com.codearena.common.Verdict;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import org.springframework.context.ApplicationEventPublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SubmissionResultService {

    static final String JUDGING_FAILED_DETAIL = "Judging failed after " + JudgeQueues.MAX_ATTEMPTS + " attempts";
    static final String JUDGING_STUCK_DETAIL = "Judging did not finish after repeated requeues";

    private static final Logger log = LoggerFactory.getLogger(SubmissionResultService.class);

    private final SubmissionRepository submissionRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;

    public SubmissionResultService(SubmissionRepository submissionRepository, ApplicationEventPublisher eventPublisher,
                                   Clock clock) {
        this.submissionRepository = submissionRepository;
        this.eventPublisher = eventPublisher;
        this.clock = clock;
    }

    @Transactional
    public void apply(JudgeResult result) {
        long id = result.submissionId();
        switch (result.verdict()) {
            case PENDING -> log.warn("Ignoring PENDING result for submission {}", id);
            case RUNNING -> {
                if (submissionRepository.markRunning(id) == 0) {
                    log.debug("Ignoring RUNNING for submission {}: not PENDING", id);
                } else {
                    eventPublisher.publishEvent(new SubmissionStatusChangedEvent(id, Verdict.RUNNING));
                }
            }
            default -> finish(id, result.verdict(), result.detail(), result.failedTestOrdinal(), result.timeMs(),
                    result.memoryKb());
        }
    }

    @Transactional
    public void markJudgingFailed(long submissionId) {
        finish(submissionId, Verdict.INTERNAL_ERROR, JUDGING_FAILED_DETAIL, null, null, null);
    }

    @Transactional
    public void markJudgingStuck(long submissionId) {
        finish(submissionId, Verdict.INTERNAL_ERROR, JUDGING_STUCK_DETAIL, null, null, null);
    }

    private void finish(long id, Verdict verdict, String detail, Integer failedTestOrdinal, Integer timeMs,
                        Integer memoryKb) {
        Instant judgedAt = clock.instant();
        int updated = submissionRepository.finishIfOpen(id, verdict, detail, failedTestOrdinal, timeMs, memoryKb,
                judgedAt);
        if (updated == 0) {
            log.info("Ignoring {} for submission {}: already final or missing", verdict, id);
        } else {
            log.info("Submission {} judged {}", id, verdict);
            eventPublisher.publishEvent(new SubmissionStatusChangedEvent(id, verdict));
            submissionRepository.findTiming(id).ifPresent(timing -> eventPublisher.publishEvent(
                    new SubmissionFinalizedEvent(id, timing.language(), verdict,
                            Duration.between(timing.createdAt(), judgedAt))));
        }
    }
}
