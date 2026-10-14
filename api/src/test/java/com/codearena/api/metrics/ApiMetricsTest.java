package com.codearena.api.metrics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.codearena.api.judging.SubmissionCreatedEvent;
import com.codearena.api.judging.SubmissionFinalizedEvent;
import com.codearena.api.ratelimit.RateLimit;
import com.codearena.common.Language;
import com.codearena.common.Verdict;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.micrometer.prometheusmetrics.PrometheusConfig;
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class ApiMetricsTest {

    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    private final ApiMetrics metrics = new ApiMetrics(registry);
    private final SubmissionMetricsListener listener = new SubmissionMetricsListener(metrics);

    @Test
    void submissionMetersUseLanguageAndVerdictTags() {
        listener.onCreated(new SubmissionCreatedEvent(1L, Language.JAVA));
        listener.onFinalized(new SubmissionFinalizedEvent(1L, Language.JAVA, Verdict.ACCEPTED, Duration.ofSeconds(2)));

        assertThat(registry.get(ApiMetrics.SUBMISSIONS_CREATED).tag("language", "java").counter().count())
                .isEqualTo(1.0);
        assertThat(registry.get(ApiMetrics.SUBMISSIONS_CREATED).tag("language", "python").counter().count())
                .isZero();
        assertThat(registry.get(ApiMetrics.SUBMISSION_VERDICTS).tags("language", "java", "verdict", "accepted")
                .counter().count()).isEqualTo(1.0);
        Timer e2e = registry.get(ApiMetrics.SUBMISSION_E2E).tag("language", "java").timer();
        assertThat(e2e.count()).isEqualTo(1);
        assertThat(e2e.totalTime(TimeUnit.MILLISECONDS)).isEqualTo(2000.0);
    }

    @Test
    void prometheusScrapeExposesHistogramBucketsAndCounterNames() {
        PrometheusMeterRegistry prometheus = new PrometheusMeterRegistry(PrometheusConfig.DEFAULT);
        new ApiMetrics(prometheus).submissionFinalized(Language.JAVA, Verdict.ACCEPTED, Duration.ofMillis(900));

        String scrape = prometheus.scrape();

        assertThat(scrape)
                .contains("codearena_submission_e2e_latency_seconds_bucket{language=\"java\",le=")
                .contains("codearena_submission_verdicts_total{language=\"java\",verdict=\"accepted\"} 1.0")
                .contains("codearena_submissions_total{language=\"python\"} 0.0")
                .contains("codearena_ratelimit_rejected_total{limiter=\"submissions\"} 0.0")
                .contains("codearena_tutor_calls_total{outcome=\"unavailable\"} 0.0")
                .contains("codearena_sweeper_requeued_total 0.0")
                .contains("codearena_leaderboard_broadcasts_total{outcome=\"ok\"} 0.0");
    }

    @Test
    void everyFinalVerdictIsPreRegisteredAndOpenStatusesAreNot() {
        assertThat(registry.get(ApiMetrics.SUBMISSION_VERDICTS).counters())
                .hasSize(Language.values().length * ApiMetrics.FINAL_VERDICTS.size());
        assertThat(registry.find(ApiMetrics.SUBMISSION_VERDICTS).tag("verdict", "pending").counter()).isNull();
        assertThat(registry.find(ApiMetrics.SUBMISSION_VERDICTS).tag("verdict", "running").counter()).isNull();
        assertThatThrownBy(() -> metrics.submissionFinalized(Language.JAVA, Verdict.RUNNING, Duration.ZERO))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void negativeLatencyCountsTheVerdictButSkipsTheTimer() {
        metrics.submissionFinalized(Language.PYTHON, Verdict.WRONG_ANSWER, Duration.ofSeconds(-1));

        assertThat(registry.get(ApiMetrics.SUBMISSION_VERDICTS).tags("language", "python", "verdict", "wrong_answer")
                .counter().count()).isEqualTo(1.0);
        assertThat(registry.get(ApiMetrics.SUBMISSION_E2E).tag("language", "python").timer().count()).isZero();
    }

    @Test
    void operationalCountersUseFixedTags() {
        metrics.rateLimitRejected(RateLimit.LOGIN_PER_IP);
        metrics.tutorCall(TutorOutcome.FALLBACK);
        metrics.sweeperRequeued(3);
        metrics.leaderboardBroadcast(true);
        metrics.leaderboardBroadcast(false);

        assertThat(registry.get(ApiMetrics.RATE_LIMIT_REJECTED).tag("limiter", "login_per_ip").counter().count())
                .isEqualTo(1.0);
        assertThat(registry.get(ApiMetrics.RATE_LIMIT_REJECTED).counters()).hasSize(RateLimit.values().length);
        assertThat(registry.get(ApiMetrics.TUTOR_CALLS).tag("outcome", "fallback").counter().count()).isEqualTo(1.0);
        assertThat(registry.get(ApiMetrics.SWEEPER_REQUEUED).counter().count()).isEqualTo(3.0);
        assertThat(registry.get(ApiMetrics.LEADERBOARD_BROADCASTS).tag("outcome", "ok").counter().count())
                .isEqualTo(1.0);
        assertThat(registry.get(ApiMetrics.LEADERBOARD_BROADCASTS).tag("outcome", "failed").counter().count())
                .isEqualTo(1.0);
    }
}
