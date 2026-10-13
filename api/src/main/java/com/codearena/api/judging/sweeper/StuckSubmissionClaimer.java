package com.codearena.api.judging.sweeper;

import com.codearena.api.judging.SubmissionResultService;
import com.codearena.api.submission.StuckSubmission;
import com.codearena.api.submission.SubmissionRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Claims stuck submissions in one short transaction. Locked rows are skipped, so concurrent api instances claim
 * disjoint sets; bumping {@code last_queued_at} keeps a claimed row out of the next sweeps until it is stuck again.
 */
@Service
public class StuckSubmissionClaimer {

    private final SubmissionRepository submissionRepository;
    private final SubmissionResultService resultService;
    private final SweeperProperties properties;
    private final Clock clock;

    public StuckSubmissionClaimer(SubmissionRepository submissionRepository, SubmissionResultService resultService,
                                  SweeperProperties properties, Clock clock) {
        this.submissionRepository = submissionRepository;
        this.resultService = resultService;
        this.properties = properties;
        this.clock = clock;
    }

    /**
     * @return ids of submissions to re-publish; submissions out of requeues are finalized as INTERNAL_ERROR instead
     */
    @Transactional
    public List<Long> claim() {
        Instant now = clock.instant();
        List<Long> toRequeue = new ArrayList<>();
        for (StuckSubmission stuck : submissionRepository.lockStuck(now.minus(properties.stuckAfter()),
                properties.batchSize())) {
            if (stuck.getAttemptsCount() >= properties.maxRequeues()) {
                resultService.markJudgingStuck(stuck.getId());
            } else {
                submissionRepository.markRequeued(stuck.getId(), now);
                toRequeue.add(stuck.getId());
            }
        }
        return toRequeue;
    }
}
