package com.codearena.worker.judge;

import static org.assertj.core.api.Assertions.assertThat;

import com.codearena.common.Verdict;
import com.codearena.worker.sandbox.SandboxOutcome;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

class VerdictMapperTest {

    @Test
    void okWithMatchingOutputIsAccepted() {
        assertThat(VerdictMapper.map(SandboxOutcome.OK, true)).isEqualTo(Verdict.ACCEPTED);
    }

    @Test
    void okWithMismatchIsWrongAnswer() {
        assertThat(VerdictMapper.map(SandboxOutcome.OK, false)).isEqualTo(Verdict.WRONG_ANSWER);
    }

    @ParameterizedTest
    @CsvSource({
            "TIMEOUT, TIME_LIMIT_EXCEEDED",
            "OOM, MEMORY_LIMIT_EXCEEDED",
            "RUNTIME_ERROR, RUNTIME_ERROR",
            "COMPILE_ERROR, COMPILATION_ERROR",
            "OUTPUT_LIMIT, WRONG_ANSWER",
            "INTERNAL_ERROR, INTERNAL_ERROR"
    })
    void failureOutcomesMapRegardlessOfOutput(SandboxOutcome outcome, Verdict expected) {
        assertThat(VerdictMapper.map(outcome, false)).isEqualTo(expected);
        assertThat(VerdictMapper.map(outcome, true)).isEqualTo(expected);
    }

    @ParameterizedTest
    @EnumSource(SandboxOutcome.class)
    void everyOutcomeMapsToAFinalVerdict(SandboxOutcome outcome) {
        assertThat(VerdictMapper.map(outcome, true)).isNotIn(Verdict.PENDING, Verdict.RUNNING);
        assertThat(VerdictMapper.map(outcome, false)).isNotIn(Verdict.PENDING, Verdict.RUNNING, Verdict.ACCEPTED);
    }
}
