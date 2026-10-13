package com.codearena.worker.sandbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.junit.jupiter.api.Test;

class TarArchivesTest {

    @Test
    void unpackStripsDockerDirectoryPrefix() {
        byte[] tar = TarArchives.pack(Map.of("out/Main.class", bytes("cafe"), "out/Main$1.class", bytes("babe")));

        Map<String, byte[]> files = TarArchives.unpack(new ByteArrayInputStream(tar), 1024);

        assertThat(files).containsOnlyKeys("Main.class", "Main$1.class");
        assertThat(new String(files.get("Main.class"), StandardCharsets.UTF_8)).isEqualTo("cafe");
    }

    @Test
    void unpackEnforcesSizeCap() {
        byte[] tar = TarArchives.pack(Map.of("out/Big.class", new byte[2048]));

        assertThatThrownBy(() -> TarArchives.unpack(new ByteArrayInputStream(tar), 1024))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void packRejectsPathTraversal() {
        assertThatThrownBy(() -> TarArchives.pack(Map.of("../etc/passwd", bytes("x"))))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static byte[] bytes(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }
}
