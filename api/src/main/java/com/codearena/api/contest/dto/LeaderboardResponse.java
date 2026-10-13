package com.codearena.api.contest.dto;

import java.util.List;

public record LeaderboardResponse(long contestId, List<LeaderboardEntry> entries) {
}
