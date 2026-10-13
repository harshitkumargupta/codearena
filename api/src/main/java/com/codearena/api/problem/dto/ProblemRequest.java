package com.codearena.api.problem.dto;

import com.codearena.api.problem.Difficulty;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record ProblemRequest(
        @NotBlank
        @Size(max = 100)
        @Pattern(regexp = "^[a-z0-9]+(-[a-z0-9]+)*$", message = "must be lowercase words separated by hyphens")
        String slug,

        @NotBlank @Size(max = 200) String title,

        @NotBlank @Size(max = 50_000) String statementMd,

        @NotNull Difficulty difficulty,

        @NotNull @Min(100) @Max(10_000) Integer timeLimitMs,

        @NotNull @Min(32) @Max(1024) Integer memoryLimitMb,

        boolean published
) {
}
