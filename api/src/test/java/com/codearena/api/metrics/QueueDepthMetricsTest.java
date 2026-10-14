package com.codearena.api.metrics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.codearena.common.JudgeQueues;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.AmqpConnectException;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.QueueInformation;

class QueueDepthMetricsTest {

    private final AmqpAdmin admin = mock(AmqpAdmin.class);
    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    private final QueueDepthMetrics metrics = new QueueDepthMetrics(admin, registry);

    @Test
    void gaugesStartUnknownAndNeverCallTheBrokerOnRead() {
        for (String queue : QueueDepthMetrics.QUEUES) {
            assertThat(depth(queue)).isNaN();
        }
        verifyNoInteractions(admin);
    }

    @Test
    void pollCachesReadyCountsPerQueue() {
        when(admin.getQueueInfo(JudgeQueues.JOBS)).thenReturn(new QueueInformation(JudgeQueues.JOBS, 12, 1));
        when(admin.getQueueInfo(JudgeQueues.RESULTS)).thenReturn(new QueueInformation(JudgeQueues.RESULTS, 0, 1));
        when(admin.getQueueInfo(JudgeQueues.JOBS_DLQ)).thenReturn(new QueueInformation(JudgeQueues.JOBS_DLQ, 2, 1));

        metrics.poll();

        assertThat(depth(JudgeQueues.JOBS)).isEqualTo(12.0);
        assertThat(depth(JudgeQueues.RESULTS)).isZero();
        assertThat(depth(JudgeQueues.JOBS_DLQ)).isEqualTo(2.0);
    }

    @Test
    void brokerFailureReportsUnknownInsteadOfAStaleValue() {
        when(admin.getQueueInfo(JudgeQueues.JOBS)).thenReturn(new QueueInformation(JudgeQueues.JOBS, 5, 1));
        metrics.poll();
        when(admin.getQueueInfo(JudgeQueues.JOBS)).thenThrow(new AmqpConnectException(new RuntimeException("down")));

        metrics.poll();

        assertThat(depth(JudgeQueues.JOBS)).isNaN();
    }

    private double depth(String queue) {
        return registry.get(QueueDepthMetrics.QUEUE_DEPTH).tag("queue", queue).gauge().value();
    }
}
