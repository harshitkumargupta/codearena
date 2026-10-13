package com.codearena.api.tutor.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record ExplainFailureRequest(@NotNull @Positive Long submissionId) {
}
