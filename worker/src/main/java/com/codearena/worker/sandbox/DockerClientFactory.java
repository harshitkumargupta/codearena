package com.codearena.worker.sandbox;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.core.DefaultDockerClientConfig;
import com.github.dockerjava.core.DockerClientConfig;
import com.github.dockerjava.core.DockerClientImpl;
import com.github.dockerjava.httpclient5.ApacheDockerHttpClient;
import java.time.Duration;

public final class DockerClientFactory {

    private static final int MAX_CONNECTIONS = 64;
    private static final Duration CONNECTION_TIMEOUT = Duration.ofSeconds(5);

    private DockerClientFactory() {
    }

    public static DockerClient create(String dockerHost) {
        DockerClientConfig config = DefaultDockerClientConfig.createDefaultConfigBuilder()
                .withDockerHost(dockerHost)
                .build();
        ApacheDockerHttpClient httpClient = new ApacheDockerHttpClient.Builder()
                .dockerHost(config.getDockerHost())
                .sslConfig(config.getSSLConfig())
                .maxConnections(MAX_CONNECTIONS)
                .connectionTimeout(CONNECTION_TIMEOUT)
                .build();
        return DockerClientImpl.getInstance(config, httpClient);
    }
}
