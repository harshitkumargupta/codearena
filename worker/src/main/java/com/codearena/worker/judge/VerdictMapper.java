package com.codearena.worker.judge;

import com.codearena.common.Verdict;
import com.codearena.worker.sandbox.SandboxOutcome;

public final class VerdictMapper {

    private VerdictMapper() {
    }

    public static Verdict map(SandboxOutcome outcome, boolean outputMatches) {
        return switch (outcome) {
            case OK -> outputMatches ? Verdict.ACCEPTED : Verdict.WRONG_ANSWER;
            case OUTPUT_LIMIT -> Verdict.WRONG_ANSWER;
            case TIMEOUT -> Verdict.TIME_LIMIT_EXCEEDED;
            case OOM -> Verdict.MEMORY_LIMIT_EXCEEDED;
            case RUNTIME_ERROR -> Verdict.RUNTIME_ERROR;
            case COMPILE_ERROR -> Verdict.COMPILATION_ERROR;
            case INTERNAL_ERROR -> Verdict.INTERNAL_ERROR;
        };
    }
}
