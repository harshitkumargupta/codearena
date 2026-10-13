package com.codearena.api.problem.dto;

import com.codearena.api.problem.TestCase;

public record TestCaseSummary(long id, int ordinal, boolean sample) {

    public static TestCaseSummary from(TestCase testCase) {
        return new TestCaseSummary(testCase.getId(), testCase.getOrdinal(), testCase.isSample());
    }
}
