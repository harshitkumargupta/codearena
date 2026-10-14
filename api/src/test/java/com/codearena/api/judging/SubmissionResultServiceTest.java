package com.codearena.api.judging;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.codearena.api.submission.SubmissionRepository;
import com.codearena.api.submission.SubmissionTiming;
import com.codearena.common.JudgeResult;
import com.codearena.common.Language;
import com.codearena.common.Verdict;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.context.ApplicationEventPublisher;

class SubmissionResultServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-13T10:00:00Z");

    private final SubmissionRepository repository = mock(SubmissionRepository.class);
    private final ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);
    private final SubmissionResultService service =
            new SubmissionResultService(repository, eventPublisher, Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void runningOnlyMovesPendingSubmissionAndAnnouncesIt() {
        when(repository.markRunning(9L)).thenReturn(1);

        service.apply(JudgeResult.running(9L));

        verify(repository).markRunning(9L);
        verify(repository, never()).finishIfOpen(anyLong(), any(), any(), any(), any(), any(), any());
        verify(eventPublisher).publishEvent(new SubmissionStatusChangedEvent(9L, Verdict.RUNNING));
    }

    @Test
    void repeatedRunningForNonPendingSubmissionIsIgnored() {
        when(repository.markRunning(9L)).thenReturn(0);

        service.apply(JudgeResult.running(9L));

        verify(repository).markRunning(9L);
        verifyNoInteractions(eventPublisher);
    }

    @ParameterizedTest
    @EnumSource(value = Verdict.class, names = {"ACCEPTED", "WRONG_ANSWER", "TIME_LIMIT_EXCEEDED",
            "MEMORY_LIMIT_EXCEEDED", "RUNTIME_ERROR", "COMPILATION_ERROR", "INTERNAL_ERROR"})
    void finalResultWritesAllFieldsOnlyIfStillOpen(Verdict verdict) {
        when(repository.finishIfOpen(9L, verdict, "detail", 2, 120, 4096, NOW)).thenReturn(1);

        service.apply(new JudgeResult(9L, verdict, 2, 120, 4096, "detail"));

        verify(repository).finishIfOpen(9L, verdict, "detail", 2, 120, 4096, NOW);
        verify(repository, never()).markRunning(anyLong());
        verify(eventPublisher).publishEvent(new SubmissionStatusChangedEvent(9L, verdict));
    }

    @Test
    void resultForAlreadyFinalSubmissionIsIgnoredWithoutError() {
        when(repository.finishIfOpen(9L, Verdict.WRONG_ANSWER, null, 3, 50, 1000, NOW)).thenReturn(0);

        service.apply(new JudgeResult(9L, Verdict.WRONG_ANSWER, 3, 50, 1000, null));

        verify(repository).finishIfOpen(9L, Verdict.WRONG_ANSWER, null, 3, 50, 1000, NOW);
        verifyNoInteractions(eventPublisher);
    }

    @Test
    void pendingResultIsIgnored() {
        service.apply(new JudgeResult(9L, Verdict.PENDING, null, null, null, null));

        verifyNoInteractions(repository);
    }

    @Test
    void deadLetteredJobMarksOpenSubmissionAsInternalError() {
        when(repository.finishIfOpen(9L, Verdict.INTERNAL_ERROR, "Judging failed after 3 attempts", null, null, null,
                NOW)).thenReturn(1);

        service.markJudgingFailed(9L);

        verify(eventPublisher).publishEvent(new SubmissionStatusChangedEvent(9L, Verdict.INTERNAL_ERROR));

        verify(repository).finishIfOpen(9L, Verdict.INTERNAL_ERROR, "Judging failed after 3 attempts", null, null,
                null, NOW);
    }

    @Test
    void sweeperGiveUpMarksOpenSubmissionAsInternalError() {
        when(repository.finishIfOpen(9L, Verdict.INTERNAL_ERROR, SubmissionResultService.JUDGING_STUCK_DETAIL, null,
                null, null, NOW)).thenReturn(1);

        service.markJudgingStuck(9L);

        verify(eventPublisher).publishEvent(new SubmissionStatusChangedEvent(9L, Verdict.INTERNAL_ERROR));

        verify(repository).finishIfOpen(9L, Verdict.INTERNAL_ERROR, SubmissionResultService.JUDGING_STUCK_DETAIL, null,
                null, null, NOW);
    }

    @Test
    void duplicateFinalResultIsAnnouncedAsFinalizedOnlyOnce() {
        JudgeResult accepted = new JudgeResult(9L, Verdict.ACCEPTED, null, 40, 5000, null);
        when(repository.finishIfOpen(anyLong(), any(), any(), any(), any(), any(), any())).thenReturn(1, 0);
        when(repository.findTiming(9L))
                .thenReturn(Optional.of(new SubmissionTiming(Language.JAVA, NOW.minusSeconds(3))));

        service.apply(accepted);
        service.apply(accepted);

        verify(eventPublisher, times(1)).publishEvent(
                new SubmissionFinalizedEvent(9L, Language.JAVA, Verdict.ACCEPTED, Duration.ofSeconds(3)));
        verify(eventPublisher, times(1)).publishEvent(new SubmissionStatusChangedEvent(9L, Verdict.ACCEPTED));
        verify(repository, times(1)).findTiming(9L);
    }
}
