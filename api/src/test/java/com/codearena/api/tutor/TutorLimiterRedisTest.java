package com.codearena.api.tutor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.codearena.api.error.TooManyRequestsException;
import com.codearena.api.metrics.ApiMetrics;
import com.codearena.api.ratelimit.RateLimiter;
import com.codearena.api.security.AuthUser;
import com.codearena.api.support.DockerAvailability;
import com.codearena.api.tutor.dto.HintRequest;
import com.codearena.api.user.Role;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Tutor limits against real Redis (Testcontainers), driven through TutorService with a mocked model.
 */
class TutorLimiterRedisTest {

    private static final GenericContainer<?> REDIS =
            new GenericContainer<>(DockerImageName.parse("redis:7")).withExposedPorts(6379);
    // Two hours before UTC midnight of the real current day: Redis expires keys on real time, so a fixed past date
    // would make the daily key (EXPIREAT midnight + 1 h) vanish at once.
    private static final LocalDate TODAY = LocalDate.now(ZoneOffset.UTC);
    private static final Instant NOW = TODAY.atTime(22, 0).toInstant(ZoneOffset.UTC);
    private static final AuthUser ALICE = new AuthUser(1L, "alice", Role.USER);
    private static final TutorProperties PROPERTIES = new TutorProperties(Duration.ofMinutes(5), 3, 10, 12_000, 8_000);

    private static LettuceConnectionFactory connectionFactory;
    private static StringRedisTemplate redis;

    private final TutorContextLoader loader = mock(TutorContextLoader.class);
    private final TutorModel model = mock(TutorModel.class);
    private final HintLogRepository hintLogs = mock(HintLogRepository.class);
    private TutorService service;

    @BeforeAll
    static void startRedis() {
        DockerAvailability.requireDocker("tutor limiter redis test");
        REDIS.start();
        connectionFactory = new LettuceConnectionFactory(
                new RedisStandaloneConfiguration(REDIS.getHost(), REDIS.getMappedPort(6379)));
        connectionFactory.afterPropertiesSet();
        redis = new StringRedisTemplate(connectionFactory);
    }

    @AfterAll
    static void stopRedis() {
        if (connectionFactory != null) {
            connectionFactory.destroy();
        }
        if (REDIS.isRunning()) {
            REDIS.stop();
        }
    }

    @BeforeEach
    void freshRedisAndServices() {
        try (var connection = connectionFactory.getConnection()) {
            connection.serverCommands().flushAll();
        }
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        when(loader.forHint(any(), any())).thenAnswer(inv -> {
            HintRequest request = inv.getArgument(1);
            return new TutorContext(request.problemId(), "P", "statement", null, null, false);
        });
        service = new TutorService(loader, new TutorPromptBuilder(PROPERTIES), model,
                new TutorLimiter(redis, PROPERTIES, clock), mock(RateLimiter.class), hintLogs, PROPERTIES,
                new ApiMetrics(new SimpleMeterRegistry()), clock);
        when(model.generate(any())).thenReturn("Think about complements.");
    }

    @Test
    void secondLevelThreeHintOnSameProblemIsOnCooldownWithRetryAfter() {
        service.hint(ALICE, new HintRequest(5L, 3, null));

        assertThatThrownBy(() -> service.hint(ALICE, new HintRequest(5L, 3, null)))
                .isInstanceOfSatisfying(TooManyRequestsException.class, ex -> {
                    assertThat(ex.getMessage()).isEqualTo("Level 3 hints for this problem are on cooldown");
                    assertThat(ex.getRetryAfterSeconds()).isBetween(295L, 300L);
                });
        service.hint(ALICE, new HintRequest(5L, 2, null));
        service.hint(ALICE, new HintRequest(6L, 3, null));
    }

    @Test
    void dailyCapIs429WithRetryAfterUntilUtcMidnight() {
        service.hint(ALICE, new HintRequest(5L, 1, null));
        service.hint(ALICE, new HintRequest(5L, 2, null));
        service.hint(ALICE, new HintRequest(6L, 1, null));

        assertThatThrownBy(() -> service.hint(ALICE, new HintRequest(7L, 1, null)))
                .isInstanceOfSatisfying(TooManyRequestsException.class, ex -> {
                    assertThat(ex.getMessage()).isEqualTo("Daily limit of 3 tutor answers reached");
                    assertThat(ex.getRetryAfterSeconds()).isEqualTo(Duration.ofHours(2).toSeconds());
                });
        assertThat(redis.opsForValue().get(TutorLimiter.dailyKey(1L, TODAY))).isEqualTo("3");
    }

    @Test
    void capRejectionDoesNotLeaveALevelThreeCooldownBehind() {
        for (long problem = 1; problem <= 3; problem++) {
            service.hint(ALICE, new HintRequest(problem, 1, null));
        }

        assertThatThrownBy(() -> service.hint(ALICE, new HintRequest(9L, 3, null)))
                .isInstanceOf(TooManyRequestsException.class);
        assertThat(redis.hasKey(TutorLimiter.cooldownKey(1L, 9L))).isFalse();
    }

    @Test
    void modelFailureConsumesNeitherCooldownNorCap() {
        when(model.generate(any())).thenThrow(new TutorUnavailableException());

        for (int i = 0; i < 5; i++) {
            assertThatThrownBy(() -> service.hint(ALICE, new HintRequest(5L, 3, null)))
                    .isInstanceOf(TutorUnavailableException.class);
        }

        assertThat(redis.hasKey(TutorLimiter.cooldownKey(1L, 5L))).isFalse();
        assertThat(redis.opsForValue().get(TutorLimiter.dailyKey(1L, TODAY))).isEqualTo("0");
        doReturn("Think about complements.").when(model).generate(any());
        assertThat(service.hint(ALICE, new HintRequest(5L, 3, null)).fallback()).isFalse();
    }

    @Test
    void fallbackConsumesNeitherCooldownNorCap() {
        when(model.generate(any())).thenReturn("```\nx\n```");

        assertThat(service.hint(ALICE, new HintRequest(5L, 1, null)).fallback()).isTrue();
        assertThat(service.hint(ALICE, new HintRequest(5L, 1, null)).fallback()).isTrue();

        assertThat(redis.opsForValue().get(TutorLimiter.dailyKey(1L, TODAY))).isEqualTo("0");
    }
}
