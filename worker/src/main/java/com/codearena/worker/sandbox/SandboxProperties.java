package com.codearena.worker.sandbox;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("codearena.sandbox")
public record SandboxProperties(
        @NotBlank String dockerHost,
        @NotBlank String javaImage,
        @NotBlank String pythonImage,
        @Min(100_000_000) @Max(1_000_000_000) long nanoCpus,
        @Min(16) @Max(1024) int pidsLimit,
        @Min(1) @Max(256) int tmpfsSizeMb,
        @Min(1024) int outputLimitBytes,
        @Min(0) int killGraceMs,
        @Min(1000) int compileTimeLimitMs,
        @Min(128) int compileMemoryMb,
        @Min(0) int javaOverheadMb,
        @Min(1024) int maxCompiledBytes,
        @NotNull Duration staleContainerAge,
        @NotNull Duration janitorInterval
) {
}
