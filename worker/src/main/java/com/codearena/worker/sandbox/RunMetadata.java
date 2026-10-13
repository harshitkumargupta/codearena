package com.codearena.worker.sandbox;

/**
 * Parses the trailer that {@code judge-run} appends to stderr after the program exits:
 * {@code @@judge-meta <cgroup memory.peak bytes> <cpu usage_usec>}.
 */
record RunMetadata(String stderr, Long peakMemoryKb, Long cpuTimeMs) {

    private static final String MARKER = "\n@@judge-meta ";

    static RunMetadata parse(String stderr) {
        int index = stderr.lastIndexOf(MARKER);
        if (index < 0) {
            return new RunMetadata(stderr, null, null);
        }
        String[] parts = stderr.substring(index + MARKER.length()).trim().split("\\s+");
        Long peakBytes = parts.length > 0 ? parseNonNegative(parts[0]) : null;
        Long cpuMicros = parts.length > 1 ? parseNonNegative(parts[1]) : null;
        return new RunMetadata(stderr.substring(0, index),
                peakBytes == null ? null : peakBytes / 1024,
                cpuMicros == null ? null : cpuMicros / 1000);
    }

    private static Long parseNonNegative(String value) {
        try {
            long parsed = Long.parseLong(value);
            return parsed < 0 ? null : parsed;
        } catch (NumberFormatException ex) {
            return null;
        }
    }
}
