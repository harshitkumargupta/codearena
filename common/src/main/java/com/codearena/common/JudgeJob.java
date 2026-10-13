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

    @Override
    public String toString() {
        return "JudgeJob[submissionId=" + submissionId + ", problemId=" + problemId + ", language=" + language
                + ", sourceCodeLength=" + Redaction.length(sourceCode) + ", timeLimitMs=" + timeLimitMs
                + ", memoryLimitMb=" + memoryLimitMb + ", testCases=" + testCases.size() + "]";
    }

    public record TestCase(int ordinal, String input, String expectedOutput) {

        @Override
        public String toString() {
            return "TestCase[ordinal=" + ordinal + ", inputLength=" + Redaction.length(input)
                    + ", expectedOutputLength=" + Redaction.length(expectedOutput) + "]";
        }
    }
}
