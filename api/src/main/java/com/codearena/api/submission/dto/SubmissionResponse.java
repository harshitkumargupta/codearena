package com.codearena.api.submission.dto;

import com.codearena.api.submission.Submission;
import com.codearena.common.Language;
import com.codearena.common.Verdict;
import java.time.Instant;

public record SubmissionResponse(
        long id,
        long userId,
        long problemId,
        Long contestId,
        Language language,
        String sourceCode,
        Verdict status,
        String verdictDetail,
        Integer failedTestOrdinal,
        Integer timeMs,
        Integer memoryKb,
        Instant createdAt,
        Instant judgedAt
) {

    public static SubmissionResponse from(Submission s) {
        return new SubmissionResponse(s.getId(), s.getUserId(), s.getProblemId(), s.getContestId(), s.getLanguage(),
                s.getSourceCode(), s.getStatus(), s.getVerdictDetail(), s.getFailedTestOrdinal(), s.getTimeMs(),
                s.getMemoryKb(), s.getCreatedAt(), s.getJudgedAt());
    }
}
