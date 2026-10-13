package com.codearena.worker.sandbox;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class RunMetadataTest {

    @Test
    void parsesTrailerAndStripsIt() {
        RunMetadata metadata = RunMetadata.parse("warning\n\n@@judge-meta 4534272 24083\n");

        assertThat(metadata.stderr()).isEqualTo("warning\n");
        assertThat(metadata.peakMemoryKb()).isEqualTo(4428);
        assertThat(metadata.cpuTimeMs()).isEqualTo(24);
    }

    @Test
    void usesLastTrailerSoProgramOutputCannotOverrideIt() {
        RunMetadata metadata = RunMetadata.parse("\n@@judge-meta 1 1\n\n@@judge-meta 2048 5000\n");

        assertThat(metadata.peakMemoryKb()).isEqualTo(2);
        assertThat(metadata.cpuTimeMs()).isEqualTo(5);
    }

    @Test
    void missingOrUnknownValuesBecomeNull() {
        assertThat(RunMetadata.parse("plain stderr").peakMemoryKb()).isNull();
        RunMetadata unknown = RunMetadata.parse("\n@@judge-meta -1 -1\n");
        assertThat(unknown.peakMemoryKb()).isNull();
        assertThat(unknown.cpuTimeMs()).isNull();
    }
}
