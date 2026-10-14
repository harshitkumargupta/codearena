package com.codearena.api.metrics;

import com.codearena.api.ratelimit.RateLimit;
import com.codearena.common.Language;
import com.codearena.common.Verdict;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Duration;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * Counters and timers recorded by the api. Every tag combination is registered up front, so dashboards show zero
 * instead of no data and the set of series is fixed at startup.
 */
@Component
public class ApiMetrics {

    public static final String SUBMISSIONS_CREATED = "codearena.submissions";
    public static final String SUBMISSION_VERDICTS = "codearena.submission.verdicts";
    public static final String SUBMISSION_E2E = "codearena.submission.e2e.latency";
    public static final String SWEEPER_REQUEUED = "codearena.sweeper.requeued";
    public static final String RATE_LIMIT_REJECTED = "codearena.ratelimit.rejected";
    public static final String TUTOR_CALLS = "codearena.tutor.calls";
    public static final String LEADERBOARD_BROADCASTS = "codearena.leaderboard.broadcasts";

    static final Set<Verdict> FINAL_VERDICTS = EnumSet.complementOf(EnumSet.of(Verdict.PENDING, Verdict.RUNNING));
    private static final Duration E2E_MIN = Duration.ofMillis(50);
    private static final Duration E2E_MAX = Duration.ofMinutes(10);

    private final Map<Language, Counter> created = new EnumMap<>(Language.class);
    private final Map<Language, Map<Verdict, Counter>> verdicts = new EnumMap<>(Language.class);
    private final Map<Language, Timer> e2eLatency = new EnumMap<>(Language.class);
    private final Map<RateLimit, Counter> rateLimitRejected = new EnumMap<>(RateLimit.class);
    private final Map<TutorOutcome, Counter> tutorCalls = new EnumMap<>(TutorOutcome.class);
    private final Counter sweeperRequeued;
    private final Counter broadcastsOk;
    private final Counter broadcastsFailed;

    public ApiMetrics(MeterRegistry registry) {
        for (Language language : Language.values()) {
            String lang = MetricTags.value(language);
            created.put(language, Counter.builder(SUBMISSIONS_CREATED)
                    .description("Submissions saved and handed to the judge queue")
                    .tag(MetricTags.LANGUAGE, lang)
                    .register(registry));
            Map<Verdict, Counter> byVerdict = new EnumMap<>(Verdict.class);
            for (Verdict verdict : FINAL_VERDICTS) {
                byVerdict.put(verdict, Counter.builder(SUBMISSION_VERDICTS)
                        .description("Final verdicts, counted once per submission when the api finalizes it")
                        .tag(MetricTags.LANGUAGE, lang)
                        .tag(MetricTags.VERDICT, MetricTags.value(verdict))
                        .register(registry));
            }
            verdicts.put(language, byVerdict);
            e2eLatency.put(language, Timer.builder(SUBMISSION_E2E)
                    .description("Time from submission created_at to judged_at")
                    .tag(MetricTags.LANGUAGE, lang)
                    .publishPercentileHistogram()
                    .minimumExpectedValue(E2E_MIN)
                    .maximumExpectedValue(E2E_MAX)
                    .register(registry));
        }
        for (RateLimit limit : RateLimit.values()) {
            rateLimitRejected.put(limit, Counter.builder(RATE_LIMIT_REJECTED)
                    .description("Requests rejected with 429 by a rate limiter")
                    .tag(MetricTags.LIMITER, MetricTags.value(limit))
                    .register(registry));
        }
        for (TutorOutcome outcome : TutorOutcome.values()) {
            tutorCalls.put(outcome, Counter.builder(TUTOR_CALLS)
                    .description("Tutor hint and explanation requests by outcome")
                    .tag(MetricTags.OUTCOME, MetricTags.value(outcome))
                    .register(registry));
        }
        sweeperRequeued = Counter.builder(SWEEPER_REQUEUED)
                .description("Stuck submissions re-published by the sweeper")
                .register(registry);
        broadcastsOk = broadcastCounter(registry, "ok");
        broadcastsFailed = broadcastCounter(registry, "failed");
    }

    public void submissionCreated(Language language) {
        created.get(language).increment();
    }

    public void submissionFinalized(Language language, Verdict verdict, Duration latency) {
        Counter counter = verdicts.get(language).get(verdict);
        if (counter == null) {
            throw new IllegalArgumentException("Not a final verdict: " + verdict);
        }
        counter.increment();
        if (latency != null && !latency.isNegative()) {
            e2eLatency.get(language).record(latency);
        }
    }

    public void sweeperRequeued(int count) {
        sweeperRequeued.increment(count);
    }

    public void rateLimitRejected(RateLimit limit) {
        rateLimitRejected.get(limit).increment();
    }

    public void tutorCall(TutorOutcome outcome) {
        tutorCalls.get(outcome).increment();
    }

    public void leaderboardBroadcast(boolean ok) {
        (ok ? broadcastsOk : broadcastsFailed).increment();
    }

    private static Counter broadcastCounter(MeterRegistry registry, String outcome) {
        return Counter.builder(LEADERBOARD_BROADCASTS)
                .description("Leaderboard broadcasts to STOMP subscribers")
                .tag(MetricTags.OUTCOME, outcome)
                .register(registry);
    }
}
