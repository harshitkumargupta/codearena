package com.codearena.api.websocket;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.codearena.api.contest.ContestJoinedEvent;
import com.codearena.api.contest.leaderboard.LeaderboardService;
import com.codearena.api.contest.leaderboard.LeaderboardUpdater;
import com.codearena.api.judging.SubmissionStatusChangedEvent;
import com.codearena.common.Verdict;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class LeaderboardUpdaterTest {

    private final LeaderboardService leaderboardService = mock(LeaderboardService.class);
    private final LeaderboardBroadcaster broadcaster = mock(LeaderboardBroadcaster.class);
    private final LeaderboardUpdater updater = new LeaderboardUpdater(leaderboardService, broadcaster);

    @Test
    void finalContestVerdictUpdatesScoreThenRequestsBroadcast() {
        when(leaderboardService.refreshForSubmission(42L)).thenReturn(Optional.of(9L));

        updater.onStatusChanged(new SubmissionStatusChangedEvent(42L, Verdict.ACCEPTED));

        verify(broadcaster).requestBroadcast(9L);
    }

    @Test
    void practiceSubmissionDoesNotBroadcast() {
        when(leaderboardService.refreshForSubmission(42L)).thenReturn(Optional.empty());

        updater.onStatusChanged(new SubmissionStatusChangedEvent(42L, Verdict.WRONG_ANSWER));

        verify(broadcaster, never()).requestBroadcast(anyLong());
    }

    @Test
    void runningNeverTouchesTheLeaderboard() {
        updater.onStatusChanged(new SubmissionStatusChangedEvent(42L, Verdict.RUNNING));

        verifyNoInteractions(leaderboardService, broadcaster);
    }

    @Test
    void joinRequestsBroadcast() {
        updater.onContestJoined(new ContestJoinedEvent(9L, 5L));

        verify(leaderboardService).recordJoin(9L, 5L);
        verify(broadcaster).requestBroadcast(9L);
    }
}
