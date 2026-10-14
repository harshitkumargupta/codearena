package com.codearena.worker.messaging;

import static org.assertj.core.api.Assertions.assertThat;

import com.codearena.common.Language;
import com.codearena.common.Verdict;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.micrometer.prometheusmetrics.PrometheusConfig;
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry;
import java.time.Duration;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;

class JudgeMetricsTest {

    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    private final JudgeMetrics metrics = new JudgeMetrics(registry);

    @Test
    void durationTimerHasHistogramBucketsForPercentiles() {
        metrics.recordJudge(Language.JAVA, Verdict.TIME_LIMIT_EXCEEDED, Duration.ofMillis(2300));

        Timer timer = registry.get(JudgeMetrics.JUDGE_DURATION)
                .tags("language", "java", "verdict", "time_limit_exceeded").timer();
        assertThat(timer.count()).isEqualTo(1);
    }

    @Test
    void prometheusScrapeExposesHistogramBuckets() {
        PrometheusMeterRegistry prometheus = new PrometheusMeterRegistry(PrometheusConfig.DEFAULT);
        new JudgeMetrics(prometheus).recordJudge(Language.PYTHON, Verdict.ACCEPTED, Duration.ofMillis(700));

        assertThat(prometheus.scrape())
                .contains("codearena_judge_duration_seconds_bucket{language=\"python\",verdict=\"accepted\",le=")
                .contains("codearena_judge_duration_seconds_count{language=\"python\",verdict=\"accepted\"} 1");
    }

    @Test
    void openStatusesAreNotTimed() {
        metrics.recordJudge(Language.JAVA, Verdict.RUNNING, Duration.ofSeconds(1));

        assertThat(registry.find(JudgeMetrics.JUDGE_DURATION).tag("verdict", "running").timer()).isNull();
    }

    @Test
    void everyMeterInTheRegistryUsesOnlyAllowedTagKeys() {
        for (Language language : Language.values()) {
            for (Verdict verdict : Verdict.values()) {
                metrics.recordJudge(language, verdict, Duration.ofMillis(500));
            }
        }

        Map<String, TreeSet<String>> offending = new TreeMap<>();
        for (Meter meter : registry.getMeters()) {
            for (Tag tag : meter.getId().getTags()) {
                if (!JudgeMetrics.ALLOWED_TAGS.contains(tag.getKey())) {
                    offending.computeIfAbsent(meter.getId().getName(), k -> new TreeSet<>()).add(tag.getKey());
                }
            }
        }
        assertThat(registry.getMeters()).isNotEmpty();
        assertThat(offending).as("meters with tag keys outside %s", JudgeMetrics.ALLOWED_TAGS).isEmpty();
    }
}
