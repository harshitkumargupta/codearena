package com.codearena.api.contest.scoring;

import java.time.Instant;

public record ContestWindow(Instant start, Instant end) {

    public boolean contains(Instant time) {
        return !time.isBefore(start) && time.isBefore(end);
    }
}
