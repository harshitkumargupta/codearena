package com.codearena.api.contest.dto;

import com.codearena.api.contest.ContestStatus;
import java.time.Instant;

public record ContestSummary(long id, String title, Instant startTime, Instant endTime, int penaltyMinutes,
                             ContestStatus status) {
}
