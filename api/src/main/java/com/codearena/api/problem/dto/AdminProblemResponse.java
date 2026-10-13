package com.codearena.api.problem.dto;

import com.codearena.api.problem.Difficulty;
import com.codearena.api.problem.Problem;
import java.time.Instant;

public record AdminProblemResponse(
        long id,
        String slug,
        String title,
        String statementMd,
        Difficulty difficulty,
        int timeLimitMs,
        int memoryLimitMb,
        boolean published,
        Instant createdAt
) {

    public static AdminProblemResponse from(Problem p) {
        return new AdminProblemResponse(p.getId(), p.getSlug(), p.getTitle(), p.getStatementMd(), p.getDifficulty(),
                p.getTimeLimitMs(), p.getMemoryLimitMb(), p.isPublished(), p.getCreatedAt());
    }
}
