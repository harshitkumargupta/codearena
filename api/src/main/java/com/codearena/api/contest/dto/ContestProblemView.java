package com.codearena.api.contest.dto;

import com.codearena.api.problem.Difficulty;

public record ContestProblemView(String label, long problemId, String slug, String title, Difficulty difficulty) {
}
