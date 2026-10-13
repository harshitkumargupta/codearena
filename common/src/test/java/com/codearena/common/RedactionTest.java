package com.codearena.common;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class RedactionTest {

    private static final String SOURCE = "class Main { SECRET_SOURCE_1d4e }";
    private static final String INPUT = "HIDDEN_INPUT_7f3a";
    private static final String EXPECTED = "HIDDEN_EXPECTED_9b2c";

    @Test
    void judgeJobNeverPrintsSourceOrTestData() {
        JudgeJob job = new JudgeJob(12L, 3L, Language.JAVA, SOURCE, 2000, 256,
                List.of(new JudgeJob.TestCase(1, INPUT, EXPECTED), new JudgeJob.TestCase(2, INPUT, EXPECTED)));

        String printed = job + " " + job.testCases() + " " + String.valueOf(job);

        assertThat(printed).doesNotContain("SECRET_SOURCE", INPUT, EXPECTED);
        assertThat(job.toString()).isEqualTo("JudgeJob[submissionId=12, problemId=3, language=JAVA, "
                + "sourceCodeLength=" + SOURCE.length() + ", timeLimitMs=2000, memoryLimitMb=256, testCases=2]");
    }

    @Test
    void testCaseNeverPrintsInputOrExpectedOutput() {
        JudgeJob.TestCase testCase = new JudgeJob.TestCase(4, INPUT, EXPECTED);

        assertThat(testCase.toString()).isEqualTo("TestCase[ordinal=4, inputLength=17, expectedOutputLength=20]");
    }

    @Test
    void judgeResultNeverPrintsDetail() {
        JudgeResult result = new JudgeResult(12L, Verdict.COMPILATION_ERROR, null, null, null,
                "Main.java:1: error near SECRET_SOURCE_1d4e");

        assertThat(result.toString()).doesNotContain("SECRET_SOURCE").contains("detailLength=");
    }

    @Test
    void nullFieldsAreSafe() {
        JudgeJob job = new JudgeJob(1L, 1L, Language.PYTHON, null, 1000, 64, null);

        assertThat(job.toString()).contains("sourceCodeLength=null", "testCases=0");
        assertThat(new JudgeJob.TestCase(1, null, null).toString()).contains("inputLength=null");
        assertThat(JudgeResult.running(5L).toString()).contains("verdict=RUNNING", "detailLength=null");
    }

    @Test
    void recordEqualityStillUsesAllFields() {
        assertThat(new JudgeJob.TestCase(1, "a", "b")).isNotEqualTo(new JudgeJob.TestCase(1, "a", "c"));
    }
}
