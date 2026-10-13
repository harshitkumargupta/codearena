package com.codearena.api.tutor;

import com.codearena.common.Language;
import com.codearena.common.Verdict;

/**
 * Everything a tutor prompt may use. It holds no hidden test data by construction: only a failing case that is a
 * public sample is ever loaded into it.
 */
public record TutorContext(
        long problemId,
        String problemTitle,
        String problemStatement,
        SubmissionFacts submission,
        SampleCase failingSample,
        boolean failedOnHiddenCase
) {

    public record SubmissionFacts(long id, Language language, String sourceCode, Verdict verdict,
                                  Integer failedTestOrdinal, String compilerOutput) {
    }

    public record SampleCase(int ordinal, String input, String expectedOutput) {
    }

    public Long submissionId() {
        return submission == null ? null : submission.id();
    }
}
