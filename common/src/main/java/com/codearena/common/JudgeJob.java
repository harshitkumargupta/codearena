package com.codearena.common;

import java.util.List;

public record JudgeJob(
        long submissionId,
        long problemId,
        Language language,
        String sourceCode,
        int timeLimitMs,
        int memoryLimitMb,
        List<TestCase> testCases
) {

    public JudgeJob {
        testCases = testCases == null ? List.of() : List.copyOf(testCases);
    }

    public record TestCase(int ordinal, String input, String expectedOutput) {
    }
}
