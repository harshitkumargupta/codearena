package com.codearena.api.contest;

import java.time.Instant;
import org.springframework.test.util.ReflectionTestUtils;

final class ContestFixtures {

    static final Instant START = Instant.parse("2026-10-13T10:00:00Z");
    static final Instant END = Instant.parse("2026-10-13T12:00:00Z");

    private ContestFixtures() {
    }

    static Contest contest(long id) {
        Contest contest = new Contest("Weekly #1", START, END, 20, 1L);
        ReflectionTestUtils.setField(contest, "id", id);
        return contest;
    }
}
