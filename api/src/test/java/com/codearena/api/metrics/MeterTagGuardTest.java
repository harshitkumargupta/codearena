package com.codearena.api.metrics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.codearena.api.judging.SubmissionCreatedEvent;
import com.codearena.api.judging.SubmissionFinalizedEvent;
import com.codearena.api.ratelimit.RateLimit;
import com.codearena.common.Language;
import com.codearena.common.Verdict;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.QueueInformation;
import org.springframework.messaging.MessageHeaders;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.messaging.SessionConnectedEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

/**
 * Exercises every api meter, then scans everything in the registry: any tag key outside {@link MetricTags#ALLOWED}
 * fails the build, whichever meter added it.
 */
class MeterTagGuardTest {

    @Test
    void everyMeterInTheRegistryUsesOnlyAllowedTagKeys() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        exerciseEveryMeter(registry);

        Map<String, TreeSet<String>> offending = new TreeMap<>();
        for (Meter meter : registry.getMeters()) {
            for (Tag tag : meter.getId().getTags()) {
                if (!MetricTags.ALLOWED.contains(tag.getKey())) {
                    offending.computeIfAbsent(meter.getId().getName(), k -> new TreeSet<>()).add(tag.getKey());
                }
            }
        }

        assertThat(registry.getMeters()).hasSizeGreaterThan(20);
        assertThat(offending).as("meters with tag keys outside %s", MetricTags.ALLOWED).isEmpty();
    }

    private static void exerciseEveryMeter(SimpleMeterRegistry registry) {
        ApiMetrics metrics = new ApiMetrics(registry);
        SubmissionMetricsListener listener = new SubmissionMetricsListener(metrics);
        for (Language language : Language.values()) {
            listener.onCreated(new SubmissionCreatedEvent(1L, language));
            for (Verdict verdict : ApiMetrics.FINAL_VERDICTS) {
                listener.onFinalized(new SubmissionFinalizedEvent(1L, language, verdict, Duration.ofMillis(800)));
            }
        }
        for (RateLimit limit : RateLimit.values()) {
            metrics.rateLimitRejected(limit);
        }
        for (TutorOutcome outcome : TutorOutcome.values()) {
            metrics.tutorCall(outcome);
        }
        metrics.sweeperRequeued(1);
        metrics.leaderboardBroadcast(true);
        metrics.leaderboardBroadcast(false);

        AmqpAdmin admin = mock(AmqpAdmin.class);
        when(admin.getQueueInfo(anyString())).thenAnswer(inv -> new QueueInformation(inv.getArgument(0), 1, 1));
        new QueueDepthMetrics(admin, registry).poll();

        WebSocketSessionMetrics sessions = new WebSocketSessionMetrics(registry);
        var message = MessageBuilder.createMessage(new byte[0],
                new MessageHeaders(Map.of(SimpMessageHeaderAccessor.SESSION_ID_HEADER, "s1")));
        sessions.onConnected(new SessionConnectedEvent(MeterTagGuardTest.class, message));
        sessions.onDisconnect(new SessionDisconnectEvent(MeterTagGuardTest.class, message, "s1", CloseStatus.NORMAL));
    }
}
