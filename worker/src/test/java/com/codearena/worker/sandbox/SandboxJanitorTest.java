package com.codearena.worker.sandbox;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.model.Container;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SandboxJanitorTest {

    private static final Instant NOW = Instant.parse("2026-10-13T10:00:00Z");

    private final DockerClient docker = mock(DockerClient.class, RETURNS_DEEP_STUBS);
    private final SandboxJanitor janitor =
            new SandboxJanitor(docker, Duration.ofMinutes(2), Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void removesOnlyContainersOlderThanStaleAge() {
        Container old = container("old", NOW.minusSeconds(600));
        Container fresh = container("fresh", NOW.minusSeconds(10));
        when(docker.listContainersCmd()
                .withShowAll(true)
                .withLabelFilter(Map.of(ContainerExecutor.SANDBOX_LABEL, "true"))
                .exec()).thenReturn(List.of(old, fresh));

        janitor.removeStaleContainers();

        verify(docker).removeContainerCmd("old");
        verify(docker, never()).removeContainerCmd("fresh");
    }

    @Test
    void dockerFailureIsLoggedNotThrown() {
        when(docker.listContainersCmd()).thenThrow(new IllegalStateException("daemon down"));

        janitor.removeStaleContainers();

        verify(docker, never()).removeContainerCmd(anyString());
    }

    private static Container container(String id, Instant created) {
        Container container = mock(Container.class);
        when(container.getId()).thenReturn(id);
        when(container.getCreated()).thenReturn(created.getEpochSecond());
        return container;
    }
}
