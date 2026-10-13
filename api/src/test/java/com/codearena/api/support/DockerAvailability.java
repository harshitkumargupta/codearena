package com.codearena.api.support;

import org.junit.jupiter.api.Assumptions;
import org.testcontainers.DockerClientFactory;

public final class DockerAvailability {

    private DockerAvailability() {
    }

    public static void requireDocker(String testName) {
        if (!DockerClientFactory.instance().isDockerAvailable()) {
            String reason = "Docker is not available for Testcontainers. Start Docker, or set DOCKER_HOST "
                    + "(and TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=/var/run/docker.sock on Colima)";
            System.err.println("[" + testName + " skipped] " + reason);
            Assumptions.abort(reason);
        }
    }
}
