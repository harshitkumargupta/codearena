package com.codearena.api.contest.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;

public record ContestRequest(
        @NotBlank @Size(max = 200) String title,
        @NotNull Instant startTime,
        @NotNull Instant endTime,
        @Min(0) @Max(1000) Integer penaltyMinutes,
        @NotEmpty @Size(max = 26) List<@Valid @NotNull ContestProblemRequest> problems
) {
}
