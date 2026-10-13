package com.codearena.worker.sandbox;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.exception.NotFoundException;
import com.github.dockerjava.api.model.Container;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * Removes sandbox containers left behind by a worker that died mid-run. Only containers older than
 * {@code staleContainerAge} are touched, which is far beyond any sandbox's lifetime, so containers that a live
 * worker on the same Docker host is still using are never removed.
 */
public class SandboxJanitor {

    private static final Logger log = LoggerFactory.getLogger(SandboxJanitor.class);

    private final DockerClient docker;
    private final Duration staleContainerAge;
    private final Clock clock;

    public SandboxJanitor(DockerClient docker, Duration staleContainerAge, Clock clock) {
        this.docker = docker;
        this.staleContainerAge = staleContainerAge;
        this.clock = clock;
    }

    @Scheduled(initialDelay = 0, fixedDelayString = "${codearena.sandbox.janitor-interval}")
    public void removeStaleContainers() {
        try {
            long cutoffEpochSecond = clock.instant().minus(staleContainerAge).getEpochSecond();
            List<Container> stale = docker.listContainersCmd()
                    .withShowAll(true)
                    .withLabelFilter(Map.of(ContainerExecutor.SANDBOX_LABEL, "true"))
                    .exec()
                    .stream()
                    .filter(container -> container.getCreated() != null && container.getCreated() < cutoffEpochSecond)
                    .toList();
            for (Container container : stale) {
                remove(container.getId());
            }
            if (!stale.isEmpty()) {
                log.warn("Removed {} stale sandbox container(s)", stale.size());
            }
        } catch (RuntimeException ex) {
            log.warn("Sandbox janitor could not reach Docker: {}", ex.toString());
        }
    }

    private void remove(String id) {
        try {
            docker.removeContainerCmd(id).withForce(true).withRemoveVolumes(true).exec();
        } catch (NotFoundException ex) {
            log.debug("Stale container {} already removed", id);
        }
    }
}
