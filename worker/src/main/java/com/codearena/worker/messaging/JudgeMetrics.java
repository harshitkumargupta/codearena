package com.codearena.worker.messaging;

import com.codearena.common.Language;
import com.codearena.common.Verdict;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Duration;
import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Time spent judging one delivery (compile plus every test-case run), recorded per attempt. Verdict counts are not
 * kept here: a redelivered job is judged again, so the api counts verdicts once when it finalizes a submission.
 */
public class JudgeMetrics {

    public static final String JUDGE_DURATION = "codearena.judge.duration";
    public static final Set<String> ALLOWED_TAGS = Set.of("language", "verdict");

    private static final Duration MIN = Duration.ofMillis(10);
    private static final Duration MAX = Duration.ofMinutes(5);

    private final Map<Language, Map<Verdict, Timer>> timers = new EnumMap<>(Language.class);

    public JudgeMetrics(MeterRegistry registry) {
        for (Language language : Language.values()) {
            Map<Verdict, Timer> byVerdict = new EnumMap<>(Verdict.class);
            for (Verdict verdict : Verdict.values()) {
                if (verdict == Verdict.PENDING || verdict == Verdict.RUNNING) {
                    continue;
                }
                byVerdict.put(verdict, Timer.builder(JUDGE_DURATION)
                        .description("Time to judge one job delivery in the worker")
                        .tag("language", language.name().toLowerCase(Locale.ROOT))
                        .tag("verdict", verdict.name().toLowerCase(Locale.ROOT))
                        .publishPercentileHistogram()
                        .minimumExpectedValue(MIN)
                        .maximumExpectedValue(MAX)
                        .register(registry));
            }
            timers.put(language, byVerdict);
        }
    }

    public void recordJudge(Language language, Verdict verdict, Duration duration) {
        Timer timer = timers.get(language).get(verdict);
        if (timer != null) {
            timer.record(duration);
        }
    }
}
