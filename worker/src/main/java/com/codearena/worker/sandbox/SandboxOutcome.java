package com.codearena.worker.sandbox;

public enum SandboxOutcome {
    OK,
    TIMEOUT,
    OOM,
    OUTPUT_LIMIT,
    COMPILE_ERROR,
    RUNTIME_ERROR,
    INTERNAL_ERROR
}
