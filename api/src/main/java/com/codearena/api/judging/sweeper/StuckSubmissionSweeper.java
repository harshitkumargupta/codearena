package com.codearena.api.judging.sweeper;

import com.codearena.api.judging.JudgeJobPublisher;
import com.codearena.api.metrics.ApiMetrics;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "codearena.sweeper.enabled", havingValue = "true", matchIfMissing = true)
public class StuckSubmissionSweeper {

    private static final Logger log = LoggerFactory.getLogger(StuckSubmissionSweeper.class);

    private final StuckSubmissionClaimer claimer;
    private final JudgeJobPublisher publisher;
    private final ApiMetrics metrics;

    public StuckSubmissionSweeper(StuckSubmissionClaimer claimer, JudgeJobPublisher publisher, ApiMetrics metrics) {
        this.claimer = claimer;
        this.publisher = publisher;
        this.metrics = metrics;
    }

    @Scheduled(initialDelayString = "${codearena.sweeper.interval}", fixedDelayString = "${codearena.sweeper.interval}")
    public void sweep() {
        try {
            List<Long> requeued = claimer.claim();
            requeued.forEach(publisher::publish);
            metrics.sweeperRequeued(requeued.size());
            if (!requeued.isEmpty()) {
                log.info("Requeued {} stuck submission(s): {}", requeued.size(), requeued);
            }
        } catch (RuntimeException ex) {
            log.error("Stuck-submission sweep failed", ex);
        }
    }
}
