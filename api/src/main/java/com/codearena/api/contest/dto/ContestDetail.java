package com.codearena.api.contest.dto;

import com.codearena.api.contest.ContestStatus;
import java.time.Instant;
import java.util.List;

public record ContestDetail(
        long id,
        String title,
        Instant startTime,
        Instant endTime,
        int penaltyMinutes,
        ContestStatus status,
        boolean problemsVisible,
        List<ContestProblemView> problems
) {
}
