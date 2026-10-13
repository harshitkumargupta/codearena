package com.codearena.common;

public record JudgeResult(
        long submissionId,
        Verdict verdict,
        Integer failedTestOrdinal,
        Integer timeMs,
        Integer memoryKb,
        String detail
) {
}
