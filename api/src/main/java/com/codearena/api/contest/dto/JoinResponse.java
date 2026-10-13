package com.codearena.api.contest.dto;

public record JoinResponse(long contestId, long userId, boolean alreadyJoined) {
}
