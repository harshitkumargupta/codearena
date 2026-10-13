package com.codearena.api.contest.leaderboard;

import com.codearena.api.contest.ContestJoinedEvent;
import com.codearena.api.judging.SubmissionFinalizedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Updates the leaderboard after the database change has committed. Failures are logged and swallowed on purpose:
 * the database stays the source of truth, and the next update or a rebuild corrects Redis.
 */
@Component
public class LeaderboardUpdater {

    private static final Logger log = LoggerFactory.getLogger(LeaderboardUpdater.class);

    private final LeaderboardService leaderboardService;

    public LeaderboardUpdater(LeaderboardService leaderboardService) {
        this.leaderboardService = leaderboardService;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onSubmissionFinalized(SubmissionFinalizedEvent event) {
        try {
            leaderboardService.refreshForSubmission(event.submissionId());
        } catch (RuntimeException ex) {
            log.warn("Could not update leaderboard for submission {}: {}", event.submissionId(), ex.toString());
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onContestJoined(ContestJoinedEvent event) {
        try {
            leaderboardService.recordJoin(event.contestId(), event.userId());
        } catch (RuntimeException ex) {
            log.warn("Could not add user {} to leaderboard of contest {}: {}", event.userId(), event.contestId(),
                    ex.toString());
        }
    }
}
