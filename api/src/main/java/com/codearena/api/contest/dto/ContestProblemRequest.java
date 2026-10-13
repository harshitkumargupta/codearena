package com.codearena.api.contest.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;

public record ContestProblemRequest(
        @NotNull @Positive Long problemId,
        @NotBlank @Pattern(regexp = "^[A-Z]$", message = "must be a single letter A-Z") String label
) {
}
