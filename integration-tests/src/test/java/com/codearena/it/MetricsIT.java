package com.codearena.it;

import static org.assertj.core.api.Assertions.assertThat;

import com.codearena.api.judging.SubmissionResultService;
import com.codearena.api.metrics.ApiMetrics;
import com.codearena.api.metrics.MetricTags;
import com.codearena.common.JudgeResult;
import com.codearena.common.Verdict;
import com.codearena.worker.messaging.JudgeMetrics;
import com.fasterxml.jackson.databind.JsonNode;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Timer;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.DoubleSupplier;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

/**
 * Meters recorded by the real api and worker contexts: a judged submission is counted once, a duplicate result for
 * it changes nothing, and every CodeArena meter in either registry uses only allowed tag keys.
 */
@ExtendWith(CodeArenaStackExtension.class)
class MetricsIT {

    private static final Duration METER_WAIT = Duration.ofSeconds(10);

    private static CodeArenaStack stack;
    private static ApiClient client;

    @BeforeAll
    static void startStack(CodeArenaStack started) {
        stack = started;
        client = new ApiClient(stack.baseUrl());
    }

    @Test
    void duplicateResultDoesNotDoubleCountTheVerdict() {
        MeterRegistry api = stack.apiBean(MeterRegistry.class);
        MeterRegistry worker = stack.workerBean(MeterRegistry.class);
        DoubleSupplier accepted = () -> api.get(ApiMetrics.SUBMISSION_VERDICTS)
                .tags("language", "java", "verdict", "accepted").counter().count();
        DoubleSupplier wrong = () -> api.get(ApiMetrics.SUBMISSION_VERDICTS)
                .tags("language", "java", "verdict", "wrong_answer").counter().count();
        Timer e2e = api.get(ApiMetrics.SUBMISSION_E2E).tag("language", "java").timer();
        Timer judge = worker.get(JudgeMetrics.JUDGE_DURATION).tags("language", "java", "verdict", "accepted").timer();
        double acceptedBefore = accepted.getAsDouble();
        double wrongBefore = wrong.getAsDouble();
        long e2eBefore = e2e.count();
        long judgedBefore = judge.count();
        double createdBefore = api.get(ApiMetrics.SUBMISSIONS_CREATED).tag("language", "java").counter().count();

        ApiClient.User user = client.register("metrics");
        long id = client.submit(user, client.problemId("two-sum"), "JAVA", Solutions.TWO_SUM_JAVA, null);
        JsonNode result = client.awaitVerdict(user, id);
        assertThat(result.get("status").asText()).isEqualTo("ACCEPTED");
        awaitValue(accepted, acceptedBefore + 1);

        stack.apiBean(SubmissionResultService.class).apply(new JudgeResult(id, Verdict.ACCEPTED, null,
                result.get("timeMs").asInt(), result.get("memoryKb").asInt(), null));
        stack.apiBean(SubmissionResultService.class).apply(new JudgeResult(id, Verdict.WRONG_ANSWER, 1, 5, 5, null));

        assertThat(accepted.getAsDouble()).isEqualTo(acceptedBefore + 1);
        assertThat(wrong.getAsDouble()).isEqualTo(wrongBefore);
        assertThat(e2e.count()).isEqualTo(e2eBefore + 1);
        assertThat(judge.count()).isEqualTo(judgedBefore + 1);
        assertThat(api.get(ApiMetrics.SUBMISSIONS_CREATED).tag("language", "java").counter().count())
                .isEqualTo(createdBefore + 1);

        assertAllowedTagKeys(api);
        assertAllowedTagKeys(worker);
    }

    private static void assertAllowedTagKeys(MeterRegistry registry) {
        Map<String, TreeSet<String>> offending = new TreeMap<>();
        for (Meter meter : registry.getMeters()) {
            if (!meter.getId().getName().startsWith("codearena.")) {
                continue;
            }
            for (Tag tag : meter.getId().getTags()) {
                if (!MetricTags.ALLOWED.contains(tag.getKey())) {
                    offending.computeIfAbsent(meter.getId().getName(), k -> new TreeSet<>()).add(tag.getKey());
                }
            }
        }
        assertThat(offending).as("CodeArena meters with tag keys outside %s", MetricTags.ALLOWED).isEmpty();
    }

    private static void awaitValue(DoubleSupplier value, double expected) {
        Instant deadline = Instant.now().plus(METER_WAIT);
        while (value.getAsDouble() < expected && Instant.now().isBefore(deadline)) {
            ApiClient.pause();
        }
        assertThat(value.getAsDouble()).isEqualTo(expected);
    }
}
