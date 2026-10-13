package com.codearena.api.contest.dto;

public record LeaderboardEntry(int rank, long userId, String username, int solved, long penaltyMinutes) {
}
