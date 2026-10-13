package com.codearena.worker.sandbox;

import static org.assertj.core.api.Assertions.assertThat;

import com.codearena.common.Language;
import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.exception.NotFoundException;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.PropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.ClassPathResource;

/**
 * Runs against the real Docker daemon from {@code DOCKER_HOST} with the worker's real sandbox config.
 * The whole class is skipped with a clear reason when Docker or the judge images are unavailable.
 */
public abstract class DockerSandboxTestBase {

    protected static SandboxProperties properties;
    protected static SandboxRunner runner;
    private static DockerClient docker;

    @BeforeAll
    static void connectToDocker() {
        properties = loadSandboxProperties();
        docker = DockerClientFactory.create(properties.dockerHost());
        try {
            docker.pingCmd().exec();
        } catch (RuntimeException ex) {
            skip("Docker daemon not reachable at " + properties.dockerHost() + " (" + ex.getMessage()
                    + "). Start Docker, or set DOCKER_HOST, e.g. unix://$HOME/.colima/default/docker.sock");
        }
        for (String image : List.of(properties.javaImage(), properties.pythonImage())) {
            try {
                docker.inspectImageCmd(image).exec();
            } catch (NotFoundException ex) {
                skip("Judge image " + image + " not found. Build it with ./judge-images/build.sh");
            }
        }
        runner = new SandboxRunner(new ContainerExecutor(docker, properties), properties);
    }

    @AfterAll
    static void assertNoLeftoverContainers() throws IOException {
        if (docker == null) {
            return;
        }
        try {
            if (runner != null) {
                assertThat(docker.listContainersCmd()
                        .withShowAll(true)
                        .withLabelFilter(Map.of(ContainerExecutor.SANDBOX_LABEL, "true"))
                        .exec())
                        .as("sandbox containers left behind")
                        .isEmpty();
            }
        } finally {
            docker.close();
            docker = null;
            runner = null;
        }
    }

    protected static String source(Language language, String java, String python) {
        return language == Language.JAVA ? java : python;
    }

    protected static Program compile(Language language, String source) {
        CompileResult compiled = runner.compile(language, source);
        assertThat(compiled.succeeded())
                .as("compilation failed: %s", compiled.failure())
                .isTrue();
        return compiled.program();
    }

    private static void skip(String reason) {
        System.err.println("[sandbox tests skipped] " + reason);
        Assumptions.abort(reason);
    }

    private static SandboxProperties loadSandboxProperties() {
        StandardEnvironment environment = new StandardEnvironment();
        try {
            for (PropertySource<?> source : new YamlPropertySourceLoader()
                    .load("application", new ClassPathResource("application.yml"))) {
                environment.getPropertySources().addLast(source);
            }
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
        return Binder.get(environment).bind("codearena.sandbox", SandboxProperties.class).get();
    }
}
