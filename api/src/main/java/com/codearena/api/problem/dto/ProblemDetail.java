package com.codearena.api.problem.dto;

import com.codearena.api.problem.Difficulty;
import java.util.List;

public record ProblemDetail(
        long id,
        String slug,
        String title,
        String statementMd,
        Difficulty difficulty,
        int timeLimitMs,
        int memoryLimitMb,
        List<SampleTestCase> samples
) {
}
