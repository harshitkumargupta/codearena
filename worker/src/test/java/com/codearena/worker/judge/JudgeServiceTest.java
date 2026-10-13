package com.codearena.worker.judge;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.codearena.common.JudgeJob;
import com.codearena.common.JudgeResult;
import com.codearena.common.Language;
import com.codearena.common.Verdict;
import com.codearena.worker.sandbox.CompileResult;
import com.codearena.worker.sandbox.Program;
import com.codearena.worker.sandbox.SandboxOutcome;
import com.codearena.worker.sandbox.SandboxResult;
import com.codearena.worker.sandbox.SandboxRunner;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.InOrder;

class JudgeServiceTest {

    private static final String HIDDEN_INPUT = "HIDDEN_INPUT_7f3a";
    private static final String HIDDEN_EXPECTED = "HIDDEN_EXPECTED_9b2c";
    private static final Program PROGRAM = new Program(Language.PYTHON, Map.of("Main.py", new byte[0]));
    private static final int MAX_DETAIL = 200;

    private final SandboxRunner runner = mock(SandboxRunner.class);
    private final JudgeService service = new JudgeService(runner, new JudgeProperties(MAX_DETAIL));

    @BeforeEach
    void compilesSuccessfully() {
        when(runner.compile(any(), anyString())).thenReturn(new CompileResult(PROGRAM, null));
    }

    @Test
    void runsTestsInOrdinalOrderAndStopsAtFirstFailure() {
        stubRun("in1", ok("out1", 10, 100L));
        stubRun("in2", ok("not out2", 20, 200L));

        JudgeResult result = service.judge(job(test(3, "in3", "out3"), test(1, "in1", "out1"), test(2, "in2", "out2")));

        assertThat(result.verdict()).isEqualTo(Verdict.WRONG_ANSWER);
        assertThat(result.failedTestOrdinal()).isEqualTo(2);
        assertThat(result.detail()).isNull();
        InOrder order = inOrder(runner);
        order.verify(runner).run(PROGRAM, "in1", 1000, 256);
        order.verify(runner).run(PROGRAM, "in2", 1000, 256);
        verify(runner, never()).run(any(Program.class), eq("in3"), anyInt(), anyInt());
    }

    @Test
    void acceptedReportsMaximumTimeAndMemory() {
        stubRun("in1", ok("out1", 10, 100L));
        stubRun("in2", ok("out2", 30, null));
        stubRun("in3", ok("out3", 20, 300L));

        JudgeResult result = service.judge(job(test(1, "in1", "out1"), test(2, "in2", "out2"), test(3, "in3", "out3")));

        assertThat(result).isEqualTo(new JudgeResult(1L, Verdict.ACCEPTED, null, 30, 300, null));
    }

    @Test
    void failureStillReportsMaximumOfTestsThatRan() {
        stubRun("in1", ok("out1", 50, 500L));
        stubRun("in2", result(SandboxOutcome.TIMEOUT, "", "", 2045, null));

        JudgeResult result = service.judge(job(test(1, "in1", "out1"), test(2, "in2", "out2")));

        assertThat(result).isEqualTo(new JudgeResult(1L, Verdict.TIME_LIMIT_EXCEEDED, 2, 2045, 500, null));
    }

    @Test
    void compileErrorDetailIsTruncated() {
        when(runner.compile(any(), anyString())).thenReturn(new CompileResult(null,
                result(SandboxOutcome.COMPILE_ERROR, "", "SyntaxError: " + "x".repeat(5000), 30, null)));

        JudgeResult result = service.judge(job(test(1, "in1", "out1")));

        assertThat(result.verdict()).isEqualTo(Verdict.COMPILATION_ERROR);
        assertThat(result.failedTestOrdinal()).isNull();
        assertThat(result.detail()).hasSize(MAX_DETAIL).startsWith("SyntaxError: ").endsWith("(truncated)");
        verify(runner, never()).run(any(Program.class), anyString(), anyInt(), anyInt());
    }

    @Test
    void shortCompileErrorIsKeptWhole() {
        when(runner.compile(any(), anyString())).thenReturn(new CompileResult(null,
                result(SandboxOutcome.COMPILE_ERROR, "", "Main.java:3: error: ';' expected\n", 30, null)));

        assertThat(service.judge(job(test(1, "in1", "out1"))).detail()).isEqualTo("Main.java:3: error: ';' expected\n");
    }

    @Test
    void sandboxFailureDuringCompileIsInternalError() {
        when(runner.compile(any(), anyString())).thenReturn(new CompileResult(null,
                result(SandboxOutcome.INTERNAL_ERROR, "", "", 0, null)));

        JudgeResult result = service.judge(job(test(1, "in1", "out1")));

        assertThat(result.verdict()).isEqualTo(Verdict.INTERNAL_ERROR);
        assertThat(result.detail()).isEqualTo(JudgeService.INTERNAL_ERROR_DETAIL);
    }

    @Test
    void sandboxFailureDuringRunIsInternalErrorWithoutOrdinal() {
        stubRun("in1", result(SandboxOutcome.INTERNAL_ERROR, "", "", 0, null));

        JudgeResult result = service.judge(job(test(1, "in1", "out1")));

        assertThat(result.verdict()).isEqualTo(Verdict.INTERNAL_ERROR);
        assertThat(result.failedTestOrdinal()).isNull();
        assertThat(result.detail()).isEqualTo(JudgeService.INTERNAL_ERROR_DETAIL);
    }

    @Test
    void outputLimitIsWrongAnswerWithFixedDetail() {
        stubRun("in1", result(SandboxOutcome.OUTPUT_LIMIT, "x".repeat(100), "", 90, null));

        JudgeResult result = service.judge(job(test(1, "in1", "out1")));

        assertThat(result.verdict()).isEqualTo(Verdict.WRONG_ANSWER);
        assertThat(result.failedTestOrdinal()).isEqualTo(1);
        assertThat(result.detail()).isEqualTo(JudgeService.OUTPUT_LIMIT_DETAIL);
    }

    @Test
    void jobWithoutTestCasesIsInternalError() {
        JudgeResult result = service.judge(job());

        assertThat(result.verdict()).isEqualTo(Verdict.INTERNAL_ERROR);
        verify(runner, never()).compile(any(), anyString());
    }

    @Test
    void unexpectedExceptionBecomesInternalError() {
        when(runner.run(any(Program.class), anyString(), anyInt(), anyInt())).thenThrow(new IllegalStateException("x"));

        assertThat(service.judge(job(test(1, "in1", "out1"))).verdict()).isEqualTo(Verdict.INTERNAL_ERROR);
    }

    @ParameterizedTest
    @EnumSource(value = SandboxOutcome.class, names = {"OK", "RUNTIME_ERROR", "TIMEOUT", "OOM", "OUTPUT_LIMIT"})
    void hiddenTestDataNeverReachesDetailOrLogs(SandboxOutcome outcome) {
        String leakyOutput = "echo " + HIDDEN_INPUT + " expected " + HIDDEN_EXPECTED;
        stubRun(HIDDEN_INPUT, result(outcome, leakyOutput, "Traceback: ValueError: " + leakyOutput, 40, 1000L));

        try (LogCapture logs = new LogCapture()) {
            JudgeResult result = service.judge(job(test(1, HIDDEN_INPUT, HIDDEN_EXPECTED)));

            assertThat(result.verdict()).isNotEqualTo(Verdict.ACCEPTED);
            assertThat(result.failedTestOrdinal()).isEqualTo(1);
            assertThat(result.toString()).doesNotContain(HIDDEN_INPUT, HIDDEN_EXPECTED);
            assertThat(logs.size()).isPositive();
            assertThat(logs.text()).doesNotContain(HIDDEN_INPUT, HIDDEN_EXPECTED);
        }
    }

    private void stubRun(String input, SandboxResult result) {
        when(runner.run(PROGRAM, input, 1000, 256)).thenReturn(result);
    }

    private static SandboxResult ok(String stdout, long wallTimeMs, Long peakMemoryKb) {
        return result(SandboxOutcome.OK, stdout, "", wallTimeMs, peakMemoryKb);
    }

    private static SandboxResult result(SandboxOutcome outcome, String stdout, String stderr, long wallTimeMs,
                                        Long peakMemoryKb) {
        return new SandboxResult(outcome, stdout, stderr, outcome == SandboxOutcome.OK ? 0 : 1, wallTimeMs,
                peakMemoryKb, null, outcome == SandboxOutcome.OUTPUT_LIMIT);
    }

    private static JudgeJob.TestCase test(int ordinal, String input, String expectedOutput) {
        return new JudgeJob.TestCase(ordinal, input, expectedOutput);
    }

    private static JudgeJob job(JudgeJob.TestCase... testCases) {
        return new JudgeJob(1L, 5L, Language.PYTHON, "print(1)", 1000, 256, List.of(testCases));
    }
}
