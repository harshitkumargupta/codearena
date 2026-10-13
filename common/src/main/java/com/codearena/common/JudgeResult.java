package com.codearena.common;

public record JudgeResult(
        long submissionId,
        Verdict verdict,
        Integer failedTestOrdinal,
        Integer timeMs,
        Integer memoryKb,
        String detail
) {

    public static JudgeResult running(long submissionId) {
        return new JudgeResult(submissionId, Verdict.RUNNING, null, null, null, null);
    }

    @Override
    public String toString() {
        return "JudgeResult[submissionId=" + submissionId + ", verdict=" + verdict + ", failedTestOrdinal="
                + failedTestOrdinal + ", timeMs=" + timeMs + ", memoryKb=" + memoryKb
                + ", detailLength=" + Redaction.length(detail) + "]";
    }
}
