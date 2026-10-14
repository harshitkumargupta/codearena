package com.codearena.api.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.codearena.api.error.ServiceUnavailableException;
import com.codearena.api.error.TooManyRequestsException;
import com.codearena.api.metrics.ApiMetrics;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.BucketConfiguration;
import io.github.bucket4j.ConsumptionProbe;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

class RateLimiterTest {

    private static final RateLimitProperties.Limit PER_MINUTE_3 = new RateLimitProperties.Limit(3, Duration.ofMinutes(1));
    private static final RateLimitProperties PROPERTIES = new RateLimitProperties(List.of(), Duration.ofSeconds(2),
            new RateLimitProperties.Limit(10, Duration.ofMinutes(1)), PER_MINUTE_3, new RateLimitProperties.Limit(2, Duration.ofMinutes(1)),
            new RateLimitProperties.Limit(1, Duration.ofHours(1)), PER_MINUTE_3);

    private final InMemoryStore store = new InMemoryStore();
    private final RateLimiter limiter = new RateLimiter(store, new ClientIpResolver(List.of()), PROPERTIES,
                new ApiMetrics(new SimpleMeterRegistry()));

    @Test
    void submissionsAllowTenPerMinuteThenAnswer429WithRetryAfter() {
        for (int i = 0; i < 10; i++) {
            limiter.checkSubmission(7L);
        }

        assertThatThrownBy(() -> limiter.checkSubmission(7L))
                .isInstanceOfSatisfying(TooManyRequestsException.class, ex -> {
                    assertThat(ex.getMessage()).isEqualTo("Too many submissions. Please wait before submitting again.");
                    assertThat(ex.getRetryAfterSeconds()).isBetween(1L, 6L);
                });
        limiter.checkSubmission(8L);
        assertThat(store.keys()).contains("rl:submissions:7", "rl:submissions:8");
    }

    @Test
    void loginIsLimitedPerAddressAndPerUsernameCaseInsensitively() {
        limiter.checkLogin(request("10.0.0.1"), "Alice");
        limiter.checkLogin(request("10.0.0.2"), "alice");

        assertThatThrownBy(() -> limiter.checkLogin(request("10.0.0.3"), "ALICE "))
                .isInstanceOf(TooManyRequestsException.class)
                .hasMessage("Too many login attempts. Please wait and try again.");
        assertThat(store.keys()).contains("rl:login-ip:10.0.0.1", "rl:login-user:alice");
    }

    @Test
    void registerIsLimitedPerAddress() {
        limiter.checkRegister(request("10.0.0.1"));

        assertThatThrownBy(() -> limiter.checkRegister(request("10.0.0.1")))
                .isInstanceOfSatisfying(TooManyRequestsException.class, ex -> assertThat(ex.getRetryAfterSeconds()).isGreaterThan(3000));
        limiter.checkRegister(request("10.0.0.2"));
    }

    @Test
    void storeFailureFailsClosedWith503() {
        RateLimiter broken = new RateLimiter((key, config) -> {
            throw new IllegalStateException("Connection refused");
        }, new ClientIpResolver(List.of()), PROPERTIES,
                new ApiMetrics(new SimpleMeterRegistry()));

        assertThatThrownBy(() -> broken.checkTutor(1L))
                .isInstanceOf(ServiceUnavailableException.class)
                .hasMessage(RateLimiter.UNAVAILABLE);
    }

    private static MockHttpServletRequest request(String remote) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr(remote);
        return request;
    }

    private static final class InMemoryStore implements BucketStore {

        private final Map<String, Bucket> buckets = new HashMap<>();

        @Override
        public ConsumptionProbe tryConsume(String key, BucketConfiguration configuration) {
            return buckets.computeIfAbsent(key, k -> Bucket.builder()
                    .addLimit(configuration.getBandwidths()[0]).build()).tryConsumeAndReturnRemaining(1);
        }

        java.util.Set<String> keys() {
            return buckets.keySet();
        }
    }
}
