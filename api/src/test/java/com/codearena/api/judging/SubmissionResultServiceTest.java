package com.codearena.api.judging;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.codearena.api.submission.SubmissionRepository;
import com.codearena.common.JudgeResult;
import com.codearena.common.Verdict;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class SubmissionResultServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-13T10:00:00Z");

    private final SubmissionRepository repository = mock(SubmissionRepository.class);
    private final SubmissionResultService service =
            new SubmissionResultService(repository, Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void runningOnlyMovesPendingSubmission() {
        service.apply(JudgeResult.running(9L));

        verify(repository).markRunning(9L);
        verify(repository, never()).finishIfOpen(anyLong(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void repeatedRunningForNonPendingSubmissionIsIgnored() {
        when(repository.markRunning(9L)).thenReturn(0);

        service.apply(JudgeResult.running(9L));

        verify(repository).markRunning(9L);
    }

    @ParameterizedTest
    @EnumSource(value = Verdict.class, names = {"ACCEPTED", "WRONG_ANSWER", "TIME_LIMIT_EXCEEDED",
            "MEMORY_LIMIT_EXCEEDED", "RUNTIME_ERROR", "COMPILATION_ERROR", "INTERNAL_ERROR"})
    void finalResultWritesAllFieldsOnlyIfStillOpen(Verdict verdict) {
        when(repository.finishIfOpen(9L, verdict, "detail", 2, 120, 4096, NOW)).thenReturn(1);

        service.apply(new JudgeResult(9L, verdict, 2, 120, 4096, "detail"));

        verify(repository).finishIfOpen(9L, verdict, "detail", 2, 120, 4096, NOW);
        verify(repository, never()).markRunning(anyLong());
    }

    @Test
    void resultForAlreadyFinalSubmissionIsIgnoredWithoutError() {
        when(repository.finishIfOpen(9L, Verdict.WRONG_ANSWER, null, 3, 50, 1000, NOW)).thenReturn(0);

        service.apply(new JudgeResult(9L, Verdict.WRONG_ANSWER, 3, 50, 1000, null));

        verify(repository).finishIfOpen(9L, Verdict.WRONG_ANSWER, null, 3, 50, 1000, NOW);
    }

    @Test
    void pendingResultIsIgnored() {
        service.apply(new JudgeResult(9L, Verdict.PENDING, null, null, null, null));

        verifyNoInteractions(repository);
    }

    @Test
    void deadLetteredJobMarksOpenSubmissionAsInternalError() {
        service.markJudgingFailed(9L);

        verify(repository).finishIfOpen(9L, Verdict.INTERNAL_ERROR, "Judging failed after 3 attempts", null, null,
                null, NOW);
    }
}
