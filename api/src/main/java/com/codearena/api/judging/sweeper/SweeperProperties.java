package com.codearena.api.judging.sweeper;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("codearena.sweeper")
public record SweeperProperties(
        boolean enabled,
        @NotNull Duration interval,
        @NotNull Duration stuckAfter,
        @Min(1) int maxRequeues,
        @Min(1) @Max(1000) int batchSize
) {
}
