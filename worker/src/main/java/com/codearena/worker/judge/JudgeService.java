package com.codearena.worker.judge;

import com.codearena.common.JudgeJob;
import com.codearena.common.JudgeResult;
import com.codearena.common.Verdict;
import com.codearena.worker.sandbox.CompileResult;
import com.codearena.worker.sandbox.SandboxOutcome;
import com.codearena.worker.sandbox.SandboxResult;
import com.codearena.worker.sandbox.SandboxRunner;
import java.util.Comparator;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Compiles once, then runs test cases in ordinal order and stops at the first failure.
 * Program output and test data never leave this class: details are fixed strings except for
 * compiler output, which only reflects the submitted source.
 */
public class JudgeService {

    static final String OUTPUT_LIMIT_DETAIL = "Output limit exceeded";
    static final String INTERNAL_ERROR_DETAIL = "Internal judge error";
    static final String NO_TEST_CASES_DETAIL = "Problem has no test cases";
    private static final String TRUNCATION_SUFFIX = "\n... (truncated)";

    private static final Logger log = LoggerFactory.getLogger(JudgeService.class);

    private final SandboxRunner runner;
    private final JudgeProperties properties;

    public JudgeService(SandboxRunner runner, JudgeProperties properties) {
        this.runner = runner;
        this.properties = properties;
    }

    public JudgeResult judge(JudgeJob job) {
        try {
            JudgeResult result = doJudge(job);
            log.info("Judged submission {}: {} (failed test {}, {} ms, {} KB)", job.submissionId(),
                    result.verdict(), result.failedTestOrdinal(), result.timeMs(), result.memoryKb());
            return result;
        } catch (RuntimeException ex) {
            log.error("Judging submission {} failed", job.submissionId(), ex);
            return new JudgeResult(job.submissionId(), Verdict.INTERNAL_ERROR, null, null, null, INTERNAL_ERROR_DETAIL);
        }
    }

    private JudgeResult doJudge(JudgeJob job) {
        if (job.testCases().isEmpty()) {
            log.warn("Submission {} has no test cases", job.submissionId());
            return new JudgeResult(job.submissionId(), Verdict.INTERNAL_ERROR, null, null, null, NO_TEST_CASES_DETAIL);
        }

        CompileResult compiled = runner.compile(job.language(), job.sourceCode());
        if (!compiled.succeeded()) {
            SandboxResult failure = compiled.failure();
            Verdict verdict = VerdictMapper.map(failure.outcome(), false);
            String detail = verdict == Verdict.COMPILATION_ERROR
                    ? truncate(failure.stderr().isBlank() ? failure.stdout() : failure.stderr())
                    : INTERNAL_ERROR_DETAIL;
            return new JudgeResult(job.submissionId(), verdict, null, null, null, detail);
        }

        List<JudgeJob.TestCase> testCases = job.testCases().stream()
                .sorted(Comparator.comparingInt(JudgeJob.TestCase::ordinal))
                .toList();
        Integer maxTimeMs = null;
        Integer maxMemoryKb = null;
        for (JudgeJob.TestCase testCase : testCases) {
            SandboxResult run = runner.run(compiled.program(), testCase.input(), job.timeLimitMs(),
                    job.memoryLimitMb());
            maxTimeMs = max(maxTimeMs, run.wallTimeMs() >= 0 ? (int) run.wallTimeMs() : null);
            maxMemoryKb = max(maxMemoryKb, run.peakMemoryKb() == null ? null : run.peakMemoryKb().intValue());

            boolean matches = run.outcome() == SandboxOutcome.OK
                    && OutputComparator.matches(testCase.expectedOutput(), run.stdout());
            Verdict verdict = VerdictMapper.map(run.outcome(), matches);
            log.debug("Submission {} test {}: {} in {} ms", job.submissionId(), testCase.ordinal(), verdict,
                    run.wallTimeMs());
            if (verdict != Verdict.ACCEPTED) {
                Integer failedOrdinal = verdict == Verdict.INTERNAL_ERROR ? null : testCase.ordinal();
                return new JudgeResult(job.submissionId(), verdict, failedOrdinal, maxTimeMs, maxMemoryKb,
                        failureDetail(run.outcome()));
            }
        }
        return new JudgeResult(job.submissionId(), Verdict.ACCEPTED, null, maxTimeMs, maxMemoryKb, null);
    }

    private static String failureDetail(SandboxOutcome outcome) {
        return switch (outcome) {
            case OUTPUT_LIMIT -> OUTPUT_LIMIT_DETAIL;
            case INTERNAL_ERROR -> INTERNAL_ERROR_DETAIL;
            default -> null;
        };
    }

    private String truncate(String text) {
        int max = properties.detailMaxChars();
        return text.length() <= max ? text : text.substring(0, max - TRUNCATION_SUFFIX.length()) + TRUNCATION_SUFFIX;
    }

    private static Integer max(Integer current, Integer candidate) {
        if (candidate == null) {
            return current;
        }
        return current == null ? candidate : Math.max(current, candidate);
    }
}
