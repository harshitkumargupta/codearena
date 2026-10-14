package com.codearena.api.metrics;

import com.codearena.api.judging.SubmissionCreatedEvent;
import com.codearena.api.judging.SubmissionFinalizedEvent;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/** Records submission meters only after the transaction that created or finalized the submission commits. */
@Component
public class SubmissionMetricsListener {

    private final ApiMetrics metrics;

    public SubmissionMetricsListener(ApiMetrics metrics) {
        this.metrics = metrics;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onCreated(SubmissionCreatedEvent event) {
        metrics.submissionCreated(event.language());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onFinalized(SubmissionFinalizedEvent event) {
        metrics.submissionFinalized(event.language(), event.verdict(), event.latency());
    }
}
