package com.codearena.api.tutor;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("codearena.tutor")
public record TutorProperties(
        @NotNull Duration level3Cooldown,
        @Min(1) @Max(10_000) int dailyCap,
        @Min(1) @Max(100) int maxCodeBlockLines,
        @Min(1_000) int maxSourceChars,
        @Min(1_000) int maxStatementChars
) {
}
