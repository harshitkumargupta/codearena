package com.codearena.api.problem.dto;

import com.codearena.api.web.MaxUtf8Bytes;
import jakarta.validation.constraints.NotNull;

public record TestCaseRequest(
        @NotNull @MaxUtf8Bytes(1_048_576) String input,
        @NotNull @MaxUtf8Bytes(1_048_576) String expectedOutput,
        boolean sample
) {
}
