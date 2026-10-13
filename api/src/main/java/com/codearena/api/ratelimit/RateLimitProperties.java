package com.codearena.api.ratelimit;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("codearena.rate-limit")
public record RateLimitProperties(
        List<String> trustedProxies,
        @NotNull Duration redisTimeout,
        @Valid @NotNull Limit submissions,
        @Valid @NotNull Limit loginPerIp,
        @Valid @NotNull Limit loginPerUsername,
        @Valid @NotNull Limit registerPerIp,
        @Valid @NotNull Limit tutor
) {

    public RateLimitProperties {
        trustedProxies = trustedProxies == null ? List.of()
                : trustedProxies.stream().map(String::trim).filter(s -> !s.isEmpty()).toList();
    }

    /** {@code capacity} requests per {@code period}, refilled smoothly. */
    public record Limit(@Min(1) long capacity, @NotNull Duration period) {
    }

    public Limit limit(RateLimit rateLimit) {
        return switch (rateLimit) {
            case SUBMISSIONS -> submissions;
            case LOGIN_PER_IP -> loginPerIp;
            case LOGIN_PER_USERNAME -> loginPerUsername;
            case REGISTER_PER_IP -> registerPerIp;
            case TUTOR -> tutor;
        };
    }
}
