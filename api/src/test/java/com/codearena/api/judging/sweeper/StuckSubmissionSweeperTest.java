package com.codearena.api.judging.sweeper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.codearena.api.judging.JudgeJobPublisher;
import com.codearena.api.judging.SubmissionResultService;
import com.codearena.api.submission.StuckSubmission;
import com.codearena.api.submission.SubmissionRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;

class StuckSubmissionSweeperTest {

    private static final Instant NOW = Instant.parse("2026-10-13T10:00:00Z");
    private static final SweeperProperties PROPERTIES =
            new SweeperProperties(true, Duration.ofSeconds(30), Duration.ofMinutes(2), 3, 50);

    private final SubmissionRepository repository = mock(SubmissionRepository.class);
    private final SubmissionResultService resultService = mock(SubmissionResultService.class);
    private final JudgeJobPublisher publisher = mock(JudgeJobPublisher.class);
    private final StuckSubmissionClaimer claimer =
            new StuckSubmissionClaimer(repository, resultService, PROPERTIES, Clock.fixed(NOW, ZoneOffset.UTC));
    private final StuckSubmissionSweeper sweeper = new StuckSubmissionSweeper(claimer, publisher);

    @Test
    void looksForRowsQueuedMoreThanStuckAfterAgo() {
        when(repository.lockStuck(NOW.minus(Duration.ofMinutes(2)), 50)).thenReturn(List.of());

        assertThat(claimer.claim()).isEmpty();

        verify(repository).lockStuck(NOW.minus(Duration.ofMinutes(2)), 50);
    }

    @Test
    void stuckSubmissionsWithRequeuesLeftAreBumpedAndRepublished() {
        when(repository.lockStuck(NOW.minus(Duration.ofMinutes(2)), 50))
                .thenReturn(List.of(stuck(11L, 0), stuck(12L, 2)));

        sweeper.sweep();

        verify(repository).markRequeued(11L, NOW);
        verify(repository).markRequeued(12L, NOW);
        verify(publisher).publish(11L);
        verify(publisher).publish(12L);
        verify(resultService, never()).markJudgingStuck(anyLong());
    }

    @Test
    void submissionOutOfRequeuesIsMarkedInternalErrorAndNotRepublished() {
        when(repository.lockStuck(NOW.minus(Duration.ofMinutes(2)), 50)).thenReturn(List.of(stuck(13L, 3)));

        sweeper.sweep();

        verify(resultService).markJudgingStuck(13L);
        verify(repository, never()).markRequeued(anyLong(), org.mockito.ArgumentMatchers.any());
        verify(publisher, never()).publish(anyLong());
    }

    @Test
    void sweepFailureIsContained() {
        when(repository.lockStuck(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyInt()))
                .thenThrow(new IllegalStateException("db down"));

        sweeper.sweep();

        verify(publisher, never()).publish(anyLong());
    }

    private static StuckSubmission stuck(long id, int attempts) {
        return new StuckSubmission() {
            @Override
            public Long getId() {
                return id;
            }

            @Override
            public Integer getAttemptsCount() {
                return attempts;
            }
        };
    }
}
