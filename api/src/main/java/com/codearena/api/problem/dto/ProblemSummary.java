package com.codearena.api.problem.dto;

import com.codearena.api.problem.Difficulty;
import com.codearena.api.problem.Problem;

public record ProblemSummary(long id, String slug, String title, Difficulty difficulty) {

    public static ProblemSummary from(Problem problem) {
        return new ProblemSummary(problem.getId(), problem.getSlug(), problem.getTitle(), problem.getDifficulty());
    }
}
