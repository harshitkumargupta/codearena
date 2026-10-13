package com.codearena.api.tutor.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record HintRequest(
        @NotNull @Positive Long problemId,
        @NotNull @Min(1) @Max(3) Integer level,
        @Positive Long submissionId
) {
}
