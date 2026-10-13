package com.codearena.common;

public record JudgeJob(
        long submissionId,
        long problemId,
        Language language,
        String sourceCode,
        int timeLimitMs,
        int memoryLimitMb
) {
}
