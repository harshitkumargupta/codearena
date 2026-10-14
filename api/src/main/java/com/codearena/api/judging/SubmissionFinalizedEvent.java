package com.codearena.api.judging;

import com.codearena.common.Language;
import com.codearena.common.Verdict;
import java.time.Duration;

/**
 * Published by {@link SubmissionResultService} only when its conditional update moved a submission from an open
 * status to a final verdict, so it fires at most once per submission however often a result is delivered.
 */
public record SubmissionFinalizedEvent(long submissionId, Language language, Verdict verdict, Duration latency) {
}
