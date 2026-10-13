package com.codearena.worker.sandbox;

import java.util.List;
import java.util.Map;

record ContainerSpec(
        String image,
        List<String> command,
        long memoryBytes,
        long timeoutMs,
        Map<String, byte[]> inputFiles,
        String outputDir
) {
}
