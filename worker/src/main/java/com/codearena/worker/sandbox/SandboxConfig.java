package com.codearena.worker.sandbox;

import com.github.dockerjava.api.DockerClient;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(SandboxProperties.class)
public class SandboxConfig {

    @Bean(destroyMethod = "close")
    public DockerClient dockerClient(SandboxProperties properties) {
        return DockerClientFactory.create(properties.dockerHost());
    }

    @Bean
    public ContainerExecutor containerExecutor(DockerClient dockerClient, SandboxProperties properties) {
        return new ContainerExecutor(dockerClient, properties);
    }

    @Bean
    public SandboxRunner sandboxRunner(ContainerExecutor containerExecutor, SandboxProperties properties) {
        return new SandboxRunner(containerExecutor, properties);
    }
}
