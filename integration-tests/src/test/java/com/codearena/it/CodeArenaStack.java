package com.codearena.it;

import com.codearena.api.ApiApplication;
import com.codearena.worker.WorkerApplication;
import com.github.dockerjava.api.exception.NotFoundException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.servlet.context.ServletWebServerApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Postgres, RabbitMQ and Redis in containers, with the api and the worker booted from their own application.yml as
 * two separate Spring contexts in this JVM. Started once and shared by every test class, closed when the run ends.
 */
final class CodeArenaStack implements ExtensionContext.Store.CloseableResource {

    static final String ADMIN_USERNAME = "itadmin";
    static final String ADMIN_PASSWORD = "it-admin-password-1";
    static final List<String> JUDGE_IMAGES = List.of(
            env("JUDGE_JAVA_IMAGE", "codearena/judge-java:latest"),
            env("JUDGE_PYTHON_IMAGE", "codearena/judge-python:latest"));

    private static final String WORKER_EXCLUDES = String.join(",",
            "org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration",
            "org.springframework.boot.autoconfigure.jdbc.DataSourceTransactionManagerAutoConfiguration",
            "org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration",
            "org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration",
            "org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration",
            "org.springframework.boot.autoconfigure.data.redis.RedisRepositoriesAutoConfiguration",
            "org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration",
            "org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration");

    private final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(DockerImageName.parse("postgres:16"));
    private final RabbitMQContainer rabbit = new RabbitMQContainer(DockerImageName.parse("rabbitmq:3.13-management"));
    private final GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:7")).withExposedPorts(6379);

    private ConfigurableApplicationContext api;
    private ConfigurableApplicationContext worker;

    static CodeArenaStack get(ExtensionContext context) {
        requireDockerAndJudgeImages();
        return context.getRoot().getStore(ExtensionContext.Namespace.GLOBAL)
                .getOrComputeIfAbsent(CodeArenaStack.class, key -> start(), CodeArenaStack.class);
    }

    private static void requireDockerAndJudgeImages() {
        if (!DockerClientFactory.instance().isDockerAvailable()) {
            skip("Docker is not available for Testcontainers. Start Docker, or set DOCKER_HOST "
                    + "(and TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=/var/run/docker.sock on Colima)");
        }
        for (String image : JUDGE_IMAGES) {
            try {
                DockerClientFactory.instance().client().inspectImageCmd(image).exec();
            } catch (NotFoundException ex) {
                skip("Judge image " + image + " is missing. Build it with ./judge-images/build.sh");
            }
        }
    }

    private static void skip(String reason) {
        if ("true".equalsIgnoreCase(System.getenv("CI_REQUIRE_DOCKER"))) {
            throw new IllegalStateException(reason + " (CI_REQUIRE_DOCKER=true, so this fails instead of skipping)");
        }
        System.err.println("[integration tests skipped] " + reason);
        Assumptions.abort(reason);
    }

    private static CodeArenaStack start() {
        CodeArenaStack stack = new CodeArenaStack();
        try {
            stack.boot();
        } catch (RuntimeException ex) {
            stack.close();
            throw ex;
        }
        return stack;
    }

    private void boot() {
        postgres.start();
        rabbit.start();
        redis.start();
        Map<String, Object> rabbit = Map.of(
                "spring.rabbitmq.host", this.rabbit.getHost(),
                "spring.rabbitmq.port", this.rabbit.getAmqpPort(),
                "spring.rabbitmq.username", this.rabbit.getAdminUsername(),
                "spring.rabbitmq.password", this.rabbit.getAdminPassword());

        Map<String, Object> apiProps = new LinkedHashMap<>(rabbit);
        apiProps.put("spring.config.location", configFile("api"));
        apiProps.put("server.port", 0);
        apiProps.put("management.server.port", 0);
        apiProps.put("spring.datasource.url", postgres.getJdbcUrl());
        apiProps.put("spring.datasource.username", postgres.getUsername());
        apiProps.put("spring.datasource.password", postgres.getPassword());
        apiProps.put("spring.data.redis.host", redis.getHost());
        apiProps.put("spring.data.redis.port", redis.getMappedPort(6379));
        apiProps.put("codearena.jwt.secret", "integration-test-secret-that-is-long-enough-0123456789");
        apiProps.put("codearena.admin.username", ADMIN_USERNAME);
        apiProps.put("codearena.admin.email", "itadmin@example.com");
        apiProps.put("codearena.admin.password", ADMIN_PASSWORD);
        apiProps.put("codearena.sweeper.enabled", false);
        api = new SpringApplicationBuilder(ApiApplication.class).run(args(apiProps));

        Map<String, Object> workerProps = new LinkedHashMap<>(rabbit);
        workerProps.put("spring.config.location", configFile("worker"));
        workerProps.put("spring.autoconfigure.exclude", WORKER_EXCLUDES);
        for (String model : List.of("chat", "embedding", "embedding.text", "embedding.multimodal", "image", "audio.speech",
                "audio.transcription", "moderation")) {
            workerProps.put("spring.ai.model." + model, "none");
        }
        worker = new SpringApplicationBuilder(WorkerApplication.class).web(WebApplicationType.NONE).run(args(workerProps));
    }

    /** Command-line arguments, so they override each module's application.yml. */
    private static String[] args(Map<String, Object> properties) {
        return properties.entrySet().stream().map(e -> "--" + e.getKey() + "=" + e.getValue()).toArray(String[]::new);
    }

    private static String env(String name, String fallback) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? fallback : value;
    }

    private static String configFile(String module) {
        Path file = Path.of("..", module, "src", "main", "resources", "application.yml").toAbsolutePath().normalize();
        if (!Files.isRegularFile(file)) {
            throw new IllegalStateException("Missing " + file + "; run the tests from the integration-tests module");
        }
        return "file:" + file;
    }

    String baseUrl() {
        return "http://localhost:" + ((ServletWebServerApplicationContext) api).getWebServer().getPort();
    }

    <T> T apiBean(Class<T> type) {
        return api.getBean(type);
    }

    <T> T workerBean(Class<T> type) {
        return worker.getBean(type);
    }

    @Override
    public void close() {
        if (worker != null) {
            worker.close();
        }
        if (api != null) {
            api.close();
        }
        redis.stop();
        rabbit.stop();
        postgres.stop();
    }
}
