package com.codearena.api.contest.scoring;

/**
 * ICPC score packed into one sorted-set score: {@code solved * 10_000_000 - penaltyMinutes}. A higher packed value
 * always means more problems solved, or the same number with less penalty.
 */
public record UserScore(int solved, long penaltyMinutes) {

    public static final long SOLVED_WEIGHT = 10_000_000L;
    public static final UserScore ZERO = new UserScore(0, 0);

    public UserScore {
        if (solved < 0 || penaltyMinutes < 0 || penaltyMinutes >= SOLVED_WEIGHT) {
            throw new IllegalArgumentException("Score out of range: solved=" + solved + ", penalty=" + penaltyMinutes);
        }
    }

    public long encode() {
        return solved * SOLVED_WEIGHT - penaltyMinutes;
    }

    public static UserScore decode(long encoded) {
        long solved = Math.floorDiv(encoded + SOLVED_WEIGHT - 1, SOLVED_WEIGHT);
        return new UserScore((int) solved, solved * SOLVED_WEIGHT - encoded);
    }
}
