package com.codearena.worker.sandbox;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.InspectContainerResponse;
import com.github.dockerjava.api.command.WaitContainerResultCallback;
import com.github.dockerjava.api.exception.ConflictException;
import com.github.dockerjava.api.exception.DockerClientException;
import com.github.dockerjava.api.exception.NotFoundException;
import com.github.dockerjava.api.model.Capability;
import com.github.dockerjava.api.model.HostConfig;
import com.github.dockerjava.api.model.LogConfig;
import com.github.dockerjava.api.model.Volume;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Runs one command in a fresh, locked-down container and always removes it afterwards.
 * Inputs are copied into an anonymous volume at {@link #INPUT_DIR} (root-owned, read-only for the sandbox
 * user) because Docker refuses archive copies into tmpfs or a read-only root filesystem.
 */
public class ContainerExecutor {

    public static final String INPUT_DIR = "/input";
    public static final String WORK_DIR = "/tmp";
    public static final String SANDBOX_LABEL = "codearena.sandbox";

    private static final Logger log = LoggerFactory.getLogger(ContainerExecutor.class);
    private static final String SANDBOX_USER = "10001:10001";
    private static final Duration ATTACH_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration KILL_WAIT = Duration.ofSeconds(5);
    private static final Duration DRAIN_TIMEOUT = Duration.ofSeconds(5);

    private final DockerClient docker;
    private final SandboxProperties properties;

    public ContainerExecutor(DockerClient docker, SandboxProperties properties) {
        this.docker = docker;
        this.properties = properties;
    }

    ContainerRun execute(ContainerSpec spec) {
        String id = null;
        try {
            id = create(spec);
            docker.copyArchiveToContainerCmd(id)
                    .withRemotePath(INPUT_DIR)
                    .withTarInputStream(new ByteArrayInputStream(TarArchives.pack(spec.inputFiles())))
                    .exec();

            String containerId = id;
            try (CappedOutputCollector output =
                         new CappedOutputCollector(properties.outputLimitBytes(), () -> killAsync(containerId))) {
                docker.attachContainerCmd(id)
                        .withStdOut(true)
                        .withStdErr(true)
                        .withFollowStream(true)
                        .exec(output);
                if (!output.awaitStarted(ATTACH_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS)) {
                    throw new IllegalStateException("Timed out attaching to container " + id);
                }

                docker.startContainerCmd(id).exec();
                boolean timedOut = !awaitExit(id, spec.timeoutMs());
                if (timedOut) {
                    kill(id);
                    awaitExit(id, KILL_WAIT.toMillis());
                }
                output.awaitCompletion(DRAIN_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);

                InspectContainerResponse.ContainerState state = docker.inspectContainerCmd(id).exec().getState();
                int exitCode = state.getExitCodeLong() == null ? -1 : state.getExitCodeLong().intValue();
                boolean outputLimitExceeded = output.limitExceeded();
                Map<String, byte[]> outputFiles = spec.outputDir() != null && !timedOut && !outputLimitExceeded
                        && exitCode == 0 ? copyOut(id, spec.outputDir()) : Map.of();

                return new ContainerRun(exitCode, Boolean.TRUE.equals(state.getOOMKilled()), timedOut,
                        outputLimitExceeded, output.stdout(), output.stderr(),
                        wallTimeMs(state.getStartedAt(), state.getFinishedAt()), outputFiles);
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while running container " + id, ex);
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        } finally {
            remove(id);
        }
    }

    private String create(ContainerSpec spec) {
        HostConfig hostConfig = HostConfig.newHostConfig()
                .withNetworkMode("none")
                .withMemory(spec.memoryBytes())
                .withMemorySwap(spec.memoryBytes())
                .withNanoCPUs(properties.nanoCpus())
                .withPidsLimit((long) properties.pidsLimit())
                .withReadonlyRootfs(true)
                .withTmpFs(Map.of(WORK_DIR, "rw,nosuid,nodev,noexec,size=" + properties.tmpfsSizeMb() + "m"))
                .withCapDrop(Capability.ALL)
                .withSecurityOpts(List.of("no-new-privileges"))
                .withLogConfig(new LogConfig(LogConfig.LoggingType.NONE, null));

        List<Volume> volumes = new ArrayList<>(List.of(new Volume(INPUT_DIR)));
        if (spec.outputDir() != null) {
            volumes.add(new Volume(spec.outputDir()));
        }

        return docker.createContainerCmd(spec.image())
                .withCmd(spec.command())
                .withUser(SANDBOX_USER)
                .withWorkingDir(WORK_DIR)
                .withNetworkDisabled(true)
                .withAttachStdout(true)
                .withAttachStderr(true)
                .withVolumes(volumes)
                .withLabels(Map.of(SANDBOX_LABEL, "true"))
                .withHostConfig(hostConfig)
                .exec()
                .getId();
    }

    private boolean awaitExit(String id, long timeoutMs) {
        try (WaitContainerResultCallback callback = docker.waitContainerCmd(id).exec(new WaitContainerResultCallback())) {
            callback.awaitStatusCode(timeoutMs, TimeUnit.MILLISECONDS);
            return true;
        } catch (DockerClientException ex) {
            return false;
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

    private Map<String, byte[]> copyOut(String id, String path) throws IOException {
        try (InputStream archive = docker.copyArchiveFromContainerCmd(id, path).exec()) {
            return TarArchives.unpack(archive, properties.maxCompiledBytes());
        }
    }

    // The attach callback thread must keep draining output while Docker stops the container, or the kill blocks.
    private void killAsync(String id) {
        Thread.ofVirtual().name("sandbox-kill-" + id).start(() -> kill(id));
    }

    private void kill(String id) {
        try {
            docker.killContainerCmd(id).exec();
        } catch (NotFoundException | ConflictException ex) {
            log.debug("Container {} already stopped before kill", id);
        }
    }

    private void remove(String id) {
        if (id == null) {
            return;
        }
        try {
            docker.removeContainerCmd(id).withForce(true).withRemoveVolumes(true).exec();
        } catch (NotFoundException ex) {
            log.debug("Container {} already removed", id);
        } catch (RuntimeException ex) {
            log.warn("Failed to remove sandbox container {}", id, ex);
        }
    }

    private static long wallTimeMs(String startedAt, String finishedAt) {
        try {
            return Duration.between(Instant.parse(startedAt), Instant.parse(finishedAt)).toMillis();
        } catch (RuntimeException ex) {
            return -1;
        }
    }
}
