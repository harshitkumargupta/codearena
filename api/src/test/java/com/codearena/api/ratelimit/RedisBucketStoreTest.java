package com.codearena.api.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.codearena.api.error.ServiceUnavailableException;
import com.codearena.api.error.TooManyRequestsException;
import com.codearena.api.support.DockerAvailability;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Bucket4j against real Redis (Testcontainers): buckets are shared through Redis, and an unreachable Redis fails
 * fast with 503.
 */
class RedisBucketStoreTest {

    private static final GenericContainer<?> REDIS =
            new GenericContainer<>(DockerImageName.parse("redis:7")).withExposedPorts(6379);
    private static final RateLimitProperties.Limit FIVE_PER_MINUTE = new RateLimitProperties.Limit(5, Duration.ofMinutes(1));
    private static final RateLimitProperties PROPERTIES = new RateLimitProperties(List.of(), Duration.ofSeconds(2),
            FIVE_PER_MINUTE, FIVE_PER_MINUTE, FIVE_PER_MINUTE, FIVE_PER_MINUTE, FIVE_PER_MINUTE);

    private static RedisBucketStore store;

    @BeforeAll
    static void startRedis() {
        DockerAvailability.requireDocker("redis bucket store test");
        REDIS.start();
        store = new RedisBucketStore(REDIS.getHost(), REDIS.getMappedPort(6379), null, Duration.ofSeconds(2));
    }

    @AfterAll
    static void stopRedis() {
        if (store != null) {
            store.destroy();
        }
        if (REDIS.isRunning()) {
            REDIS.stop();
        }
    }

    @Test
    void twoLimitersShareTheSameBucketThroughRedis() {
        RateLimiter first = new RateLimiter(store, new ClientIpResolver(List.of()), PROPERTIES);
        RateLimiter second = new RateLimiter(store, new ClientIpResolver(List.of()), PROPERTIES);

        for (int i = 0; i < 3; i++) {
            first.checkSubmission(42L);
        }
        second.checkSubmission(42L);
        second.checkSubmission(42L);

        assertThatThrownBy(() -> first.checkSubmission(42L))
                .isInstanceOfSatisfying(TooManyRequestsException.class, ex -> assertThat(ex.getRetryAfterSeconds()).isBetween(1L, 12L));
    }

    @Test
    void unreachableRedisFailsClosedQuickly() {
        RedisBucketStore dead = new RedisBucketStore("127.0.0.1", 1, null, Duration.ofMillis(500));
        RateLimiter limiter = new RateLimiter(dead, new ClientIpResolver(List.of()), PROPERTIES);
        try {
            long started = System.nanoTime();
            assertThatThrownBy(() -> limiter.checkTutor(1L)).isInstanceOf(ServiceUnavailableException.class);
            assertThat(Duration.ofNanos(System.nanoTime() - started)).isLessThan(Duration.ofSeconds(5));
        } finally {
            dead.destroy();
        }
    }
}
