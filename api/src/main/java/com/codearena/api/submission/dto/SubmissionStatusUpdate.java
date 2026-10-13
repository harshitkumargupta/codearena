package com.codearena.api.submission.dto;

import com.codearena.api.submission.Submission;
import com.codearena.common.Language;
import com.codearena.common.Verdict;
import java.time.Instant;

/**
 * WebSocket payload for {@code /topic/submissions/{id}}: the GET /submissions/{id} fields without {@code sourceCode}
 * and {@code verdictDetail}, since compiler output quotes source. Clients fetch those with GET when
 * {@code detailAvailable} is true.
 */
public record SubmissionStatusUpdate(
        long id,
        long userId,
        long problemId,
        Long contestId,
        Language language,
        Verdict status,
        Integer failedTestOrdinal,
        Integer timeMs,
        Integer memoryKb,
        Instant createdAt,
        Instant judgedAt,
        boolean detailAvailable
) {

    public static SubmissionStatusUpdate from(Submission s) {
        return new SubmissionStatusUpdate(s.getId(), s.getUserId(), s.getProblemId(), s.getContestId(), s.getLanguage(),
                s.getStatus(), s.getFailedTestOrdinal(), s.getTimeMs(), s.getMemoryKb(), s.getCreatedAt(),
                s.getJudgedAt(), s.getVerdictDetail() != null && !s.getVerdictDetail().isEmpty());
    }
}
