package com.codearena.api.websocket;

import com.codearena.api.contest.dto.LeaderboardResponse;
import com.codearena.api.contest.leaderboard.LeaderboardProperties;
import com.codearena.api.contest.leaderboard.LeaderboardService;
import com.codearena.api.metrics.ApiMetrics;
import java.time.Clock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Component;

/**
 * Broadcasts a contest's top N to {@code /topic/contests/{id}/leaderboard}, at most once per broadcast interval per
 * contest, in the same shape as GET /contests/{id}/leaderboard.
 */
@Component
public class LeaderboardBroadcaster {

    private static final Logger log = LoggerFactory.getLogger(LeaderboardBroadcaster.class);

    private final LeaderboardService leaderboardService;
    private final SimpMessagingTemplate messagingTemplate;
    private final ApiMetrics metrics;
    private final Coalescer<Long> coalescer;

    public LeaderboardBroadcaster(LeaderboardService leaderboardService, SimpMessagingTemplate messagingTemplate,
                                  @Qualifier("taskScheduler") TaskScheduler scheduler,
                                  LeaderboardProperties properties, Clock clock, ApiMetrics metrics) {
        this.leaderboardService = leaderboardService;
        this.messagingTemplate = messagingTemplate;
        this.metrics = metrics;
        this.coalescer = new Coalescer<>(scheduler, properties.broadcastInterval(), clock, this::broadcast);
    }

    public void requestBroadcast(long contestId) {
        coalescer.request(contestId);
    }

    void broadcast(Long contestId) {
        try {
            LeaderboardResponse top = leaderboardService.top(contestId, null);
            messagingTemplate.convertAndSend(StompDestinations.leaderboard(contestId), top);
            metrics.leaderboardBroadcast(true);
            log.info("Broadcast leaderboard for contest {} ({} entries)", contestId, top.entries().size());
        } catch (RuntimeException ex) {
            metrics.leaderboardBroadcast(false);
            log.warn("Could not broadcast leaderboard for contest {}: {}", contestId, ex.toString());
        }
    }
}
