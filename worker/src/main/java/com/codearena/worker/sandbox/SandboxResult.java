package com.codearena.worker.sandbox;

public record SandboxResult(
        SandboxOutcome outcome,
        String stdout,
        String stderr,
        Integer exitCode,
        long wallTimeMs,
        Long peakMemoryKb,
        Long cpuTimeMs,
        boolean outputTruncated
) {

    static SandboxResult internalError() {
        return new SandboxResult(SandboxOutcome.INTERNAL_ERROR, "", "", null, 0, null, null, false);
    }
}
