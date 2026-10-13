package com.codearena.api.contest.scoring;

import com.codearena.common.Verdict;
import java.time.Instant;

public record ScoredSubmission(long id, long userId, long problemId, Verdict status, Instant createdAt) {
}
