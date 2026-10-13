package com.codearena.api.contest.leaderboard;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("codearena.leaderboard")
public record LeaderboardProperties(
        @Min(1) @Max(1000) int defaultLimit,
        @Min(1) @Max(1000) int maxLimit,
        @NotNull Duration broadcastInterval
) {
}
