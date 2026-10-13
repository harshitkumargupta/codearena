package com.codearena.worker.judge;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.codearena.common.JudgeJob;
import com.codearena.common.JudgeResult;
import com.codearena.common.Language;
import com.codearena.common.Verdict;
import com.codearena.worker.sandbox.DockerSandboxTestBase;
import com.codearena.worker.sandbox.Program;
import com.codearena.worker.sandbox.SandboxRunner;
import com.codearena.worker.testsupport.LogCapture;
import java.util.List;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class JudgeServiceDockerTest extends DockerSandboxTestBase {

    private static final int TIME_LIMIT_MS = 1500;
    private static final int MEMORY_LIMIT_MB = 128;
    private static final JudgeProperties JUDGE_PROPERTIES = new JudgeProperties(4000);

    // x == 0 loops forever, x == -1 throws, x == -2 hogs memory, x == 7 prints a wrong answer, otherwise x + y.
    private static final String JAVA_PROGRAM = """
            import java.util.*;

            public class Main {
                public static void main(String[] args) {
                    Scanner in = new Scanner(System.in);
                    long x = in.nextLong(), y = in.nextLong();
                    if (x == 0) while (true) { }
                    if (x == -1) throw new IllegalStateException("boom");
                    if (x == -2) {
                        List<byte[]> hog = new ArrayList<>();
                        while (true) hog.add(new byte[8 << 20]);
                    }
                    System.out.println(x == 7 ? x - y : x + y);
                }
            }
            """;
    private static final String PYTHON_PROGRAM = """
            x, y = map(int, input().split())
            if x == 0:
                while True:
                    pass
            if x == -1:
                raise ValueError("boom")
            if x == -2:
                hog = []
                while True:
                    hog.append(bytearray(8 << 20))
            print(x - y if x == 7 else x + y)
            """;

    @ParameterizedTest
    @EnumSource(Language.class)
    void correctProgramIsAccepted(Language language) {
        JudgeResult result = judge(language, program(language),
                test(3, "100 -1", "99"), test(1, "1 2", "3"), test(2, "40 2\r\n", "42\n\n"));

        assertThat(result.verdict()).isEqualTo(Verdict.ACCEPTED);
        assertThat(result.failedTestOrdinal()).isNull();
        assertThat(result.detail()).isNull();
        assertThat(result.timeMs()).isPositive().isLessThan(TIME_LIMIT_MS);
        assertThat(result.memoryKb()).isPositive();
    }

    @ParameterizedTest
    @EnumSource(Language.class)
    void wrongAnswerReportsFailingOrdinalOnly(Language language) {
        JudgeResult result = judge(language, program(language),
                test(1, "1 2", "3"), test(2, "7 3", "10"), test(3, "4 4", "8"));

        assertThat(result.verdict()).isEqualTo(Verdict.WRONG_ANSWER);
        assertThat(result.failedTestOrdinal()).isEqualTo(2);
        assertThat(result.detail()).isNull();
    }

    @ParameterizedTest
    @EnumSource(Language.class)
    void laterTestsAreNotRunAfterFirstFailure(Language language) {
        SandboxRunner spyRunner = spy(runner);
        JudgeService service = new JudgeService(spyRunner, JUDGE_PROPERTIES);

        JudgeResult result = service.judge(job(language, program(language),
                test(1, "1 2", "3"), test(2, "7 3", "10"), test(3, "0 0", "0"), test(4, "5 5", "10")));

        assertThat(result.verdict()).isEqualTo(Verdict.WRONG_ANSWER);
        assertThat(result.failedTestOrdinal()).isEqualTo(2);
        verify(spyRunner, times(2)).run(any(Program.class), anyString(), anyInt(), anyInt());
        verify(spyRunner, never()).run(any(Program.class), eq("0 0"), anyInt(), anyInt());
        verify(spyRunner, never()).run(any(Program.class), eq("5 5"), anyInt(), anyInt());
    }

    @ParameterizedTest
    @EnumSource(Language.class)
    void infiniteLoopIsTimeLimitExceeded(Language language) {
        JudgeResult result = judge(language, program(language), test(1, "1 2", "3"), test(2, "0 1", "1"));

        assertThat(result.verdict()).isEqualTo(Verdict.TIME_LIMIT_EXCEEDED);
        assertThat(result.failedTestOrdinal()).isEqualTo(2);
        assertThat(result.timeMs()).isGreaterThan(TIME_LIMIT_MS);
    }

    @ParameterizedTest
    @EnumSource(Language.class)
    void memoryHogIsMemoryLimitExceeded(Language language) {
        JudgeResult result = judge(language, program(language), test(1, "-2 0", "0"));

        assertThat(result.verdict()).isEqualTo(Verdict.MEMORY_LIMIT_EXCEEDED);
        assertThat(result.failedTestOrdinal()).isEqualTo(1);
    }

    @ParameterizedTest
    @EnumSource(Language.class)
    void exceptionIsRuntimeError(Language language) {
        JudgeResult result = judge(language, program(language), test(1, "1 2", "3"), test(2, "-1 0", "-1"));

        assertThat(result.verdict()).isEqualTo(Verdict.RUNTIME_ERROR);
        assertThat(result.failedTestOrdinal()).isEqualTo(2);
        assertThat(result.detail()).isNull();
    }

    @ParameterizedTest
    @EnumSource(Language.class)
    void compileErrorReturnsCompilerOutput(Language language) {
        String broken = language == Language.JAVA
                ? "public class Main { public static void main(String[] a) { int x = \"s\"; } }"
                : "def solve(:\n    pass\n";

        JudgeResult result = judge(language, broken, test(1, "1 2", "3"));

        assertThat(result.verdict()).isEqualTo(Verdict.COMPILATION_ERROR);
        assertThat(result.failedTestOrdinal()).isNull();
        assertThat(result.detail()).contains(language == Language.JAVA ? "incompatible types" : "SyntaxError");
    }

    @ParameterizedTest
    @EnumSource(Language.class)
    void hiddenTestDataNeverLeaksIntoResultOrLogs(Language language) {
        String hiddenInput = "HIDDEN_INPUT_7f3a";
        String hiddenExpected = "HIDDEN_EXPECTED_9b2c";
        String echoAndCrash = language == Language.JAVA ? """
                import java.util.Scanner;

                public class Main {
                    public static void main(String[] args) {
                        String line = new Scanner(System.in).nextLine();
                        System.out.println(line);
                        System.err.println(line);
                        throw new IllegalStateException(line);
                    }
                }
                """ : """
                import sys

                line = input()
                print(line)
                print(line, file=sys.stderr)
                raise ValueError(line)
                """;
        String echoOnly = language == Language.JAVA ? """
                public class Main {
                    public static void main(String[] args) {
                        System.out.println(new java.util.Scanner(System.in).nextLine());
                    }
                }
                """ : "print(input())\n";

        try (LogCapture logs = new LogCapture()) {
            JudgeResult crashed = judge(language, echoAndCrash, test(1, hiddenInput, hiddenExpected));
            JudgeResult wrong = judge(language, echoOnly, test(1, hiddenInput, hiddenExpected));

            assertThat(crashed.verdict()).isEqualTo(Verdict.RUNTIME_ERROR);
            assertThat(wrong.verdict()).isEqualTo(Verdict.WRONG_ANSWER);
            assertThat(wrong.failedTestOrdinal()).isEqualTo(1);
            assertThat(crashed.toString() + wrong).doesNotContain(hiddenInput, hiddenExpected);
            assertThat(logs.size()).isPositive();
            assertThat(logs.text()).doesNotContain(hiddenInput, hiddenExpected);
        }
    }

    private static String program(Language language) {
        return language == Language.JAVA ? JAVA_PROGRAM : PYTHON_PROGRAM;
    }

    private static JudgeResult judge(Language language, String source, JudgeJob.TestCase... testCases) {
        return new JudgeService(runner, JUDGE_PROPERTIES).judge(job(language, source, testCases));
    }

    private static JudgeJob job(Language language, String source, JudgeJob.TestCase... testCases) {
        return new JudgeJob(42L, 7L, language, source, TIME_LIMIT_MS, MEMORY_LIMIT_MB, List.of(testCases));
    }

    private static JudgeJob.TestCase test(int ordinal, String input, String expectedOutput) {
        return new JudgeJob.TestCase(ordinal, input, expectedOutput);
    }
}
