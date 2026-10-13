package com.codearena.worker.sandbox;

import java.util.Map;

record ContainerRun(
        int exitCode,
        boolean oomKilled,
        boolean timedOut,
        boolean outputLimitExceeded,
        String stdout,
        String stderr,
        long wallTimeMs,
        Map<String, byte[]> outputFiles
) {
}
