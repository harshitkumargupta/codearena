package com.codearena.api.metrics;

import com.codearena.common.JudgeQueues;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.QueueInformation;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Ready-message counts for the judge queues. A scrape only reads the cached values; the broker is asked once per
 * {@code codearena.metrics.queue-depth-interval}. A failed poll sets NaN so a stale depth is never reported.
 */
@Component
public class QueueDepthMetrics {

    public static final String QUEUE_DEPTH = "codearena.queue.depth";
    static final List<String> QUEUES = List.of(JudgeQueues.JOBS, JudgeQueues.RESULTS, JudgeQueues.JOBS_DLQ);

    private static final Logger log = LoggerFactory.getLogger(QueueDepthMetrics.class);

    private final AmqpAdmin amqpAdmin;
    private final Map<String, AtomicReference<Double>> depths = new LinkedHashMap<>();

    public QueueDepthMetrics(AmqpAdmin amqpAdmin, MeterRegistry registry) {
        this.amqpAdmin = amqpAdmin;
        for (String queue : QUEUES) {
            AtomicReference<Double> depth = new AtomicReference<>(Double.NaN);
            depths.put(queue, depth);
            Gauge.builder(QUEUE_DEPTH, depth, AtomicReference::get)
                    .description("Ready messages in a judge queue, polled on a schedule")
                    .tag(MetricTags.QUEUE, queue)
                    .register(registry);
        }
    }

    @Scheduled(fixedDelayString = "${codearena.metrics.queue-depth-interval}")
    public void poll() {
        depths.forEach((queue, depth) -> depth.set(read(queue)));
    }

    private double read(String queue) {
        try {
            QueueInformation info = amqpAdmin.getQueueInfo(queue);
            return info == null ? Double.NaN : info.getMessageCount();
        } catch (RuntimeException ex) {
            log.warn("Could not read depth of queue {}: {}", queue, ex.toString());
            return Double.NaN;
        }
    }
}
