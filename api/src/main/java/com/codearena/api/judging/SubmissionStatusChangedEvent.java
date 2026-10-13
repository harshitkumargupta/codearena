package com.codearena.api.judging;

import com.codearena.common.Verdict;

/**
 * Published by {@link SubmissionResultService} whenever it actually changes a submission's status: RUNNING, a final
 * verdict, or INTERNAL_ERROR from the DLQ or the sweeper. Listeners react after the transaction commits.
 */
public record SubmissionStatusChangedEvent(long submissionId, Verdict status) {

    public boolean isFinal() {
        return status != Verdict.PENDING && status != Verdict.RUNNING;
    }
}
