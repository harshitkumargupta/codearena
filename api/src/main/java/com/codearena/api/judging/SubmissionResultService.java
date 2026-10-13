package com.codearena.api.judging;

import com.codearena.api.submission.SubmissionRepository;
import com.codearena.common.JudgeQueues;
import com.codearena.common.JudgeResult;
import com.codearena.common.Verdict;
import java.time.Clock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SubmissionResultService {

    static final String JUDGING_FAILED_DETAIL = "Judging failed after " + JudgeQueues.MAX_ATTEMPTS + " attempts";

    private static final Logger log = LoggerFactory.getLogger(SubmissionResultService.class);

    private final SubmissionRepository submissionRepository;
    private final Clock clock;

    public SubmissionResultService(SubmissionRepository submissionRepository, Clock clock) {
        this.submissionRepository = submissionRepository;
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

    private void finish(long id, Verdict verdict, String detail, Integer failedTestOrdinal, Integer timeMs,
                        Integer memoryKb) {
        int updated = submissionRepository.finishIfOpen(id, verdict, detail, failedTestOrdinal, timeMs, memoryKb,
                clock.instant());
        if (updated == 0) {
            log.info("Ignoring {} for submission {}: already final or missing", verdict, id);
        } else {
            log.info("Submission {} judged {}", id, verdict);
        }
    }
}
