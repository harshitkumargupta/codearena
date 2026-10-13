package com.codearena.api.problem.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;

public record AddTestCasesRequest(@NotEmpty @Size(max = 100) List<@Valid TestCaseRequest> testCases) {
}
