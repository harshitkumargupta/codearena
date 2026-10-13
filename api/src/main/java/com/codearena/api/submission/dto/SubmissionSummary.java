package com.codearena.api.submission.dto;

import com.codearena.api.submission.Submission;
import com.codearena.common.Language;
import com.codearena.common.Verdict;
import java.time.Instant;

public record SubmissionSummary(
        long id,
        long problemId,
        Language language,
        Verdict status,
        Integer timeMs,
        Integer memoryKb,
        Instant createdAt
) {

    public static SubmissionSummary from(Submission s) {
        return new SubmissionSummary(s.getId(), s.getProblemId(), s.getLanguage(), s.getStatus(),
                s.getTimeMs(), s.getMemoryKb(), s.getCreatedAt());
    }
}
