package com.codearena.api.judging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.codearena.api.submission.SubmissionRepository;
import com.codearena.common.JudgeJob;
import com.codearena.common.JudgeQueues;
import com.codearena.common.Language;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.AmqpConnectException;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

class JudgeJobPublisherTest {

    private static final JudgeJob JOB = new JudgeJob(10L, 5L, Language.PYTHON, "print(1)", 2000, 256,
            List.of(new JudgeJob.TestCase(1, "", "1")));

    private final JudgeJobFactory factory = mock(JudgeJobFactory.class);
    private final RabbitTemplate rabbitTemplate = mock(RabbitTemplate.class);
    private static final Instant NOW = Instant.parse("2026-10-13T10:00:00Z");

    private final SubmissionRepository submissionRepository = mock(SubmissionRepository.class);
    private final JudgeJobPublisher publisher = new JudgeJobPublisher(factory, rabbitTemplate,
            new MessagingProperties(Duration.ofMillis(200)), submissionRepository, Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void publishesFullJobToJobsQueueAndWaitsForConfirm() {
        when(factory.build(10L)).thenReturn(Optional.of(JOB));
        confirmWith(true);

        assertThat(publisher.publish(10L)).isTrue();

        verify(rabbitTemplate).convertAndSend(eq(""), eq(JudgeQueues.JOBS), eq(JOB), any(CorrelationData.class));
        verify(submissionRepository).markQueued(10L, NOW);
    }

    @Test
    void brokerNackLeavesSubmissionPendingWithoutThrowing() {
        when(factory.build(10L)).thenReturn(Optional.of(JOB));
        confirmWith(false);

        assertThat(publisher.publish(10L)).isFalse();
        verify(submissionRepository, never()).markQueued(org.mockito.ArgumentMatchers.anyLong(), any());
    }

    @Test
    void missingConfirmTimesOutWithoutThrowing() {
        when(factory.build(10L)).thenReturn(Optional.of(JOB));

        assertThat(publisher.publish(10L)).isFalse();
    }

    @Test
    void brokerDownDoesNotPropagate() {
        when(factory.build(10L)).thenReturn(Optional.of(JOB));
        doThrow(new AmqpConnectException(new java.net.ConnectException("Connection refused")))
                .when(rabbitTemplate).convertAndSend(anyString(), anyString(), any(Object.class), any(CorrelationData.class));

        assertThat(publisher.publish(10L)).isFalse();
    }

    @Test
    void missingSubmissionPublishesNothing() {
        when(factory.build(10L)).thenReturn(Optional.empty());

        assertThat(publisher.publish(10L)).isFalse();
        verify(rabbitTemplate, never()).convertAndSend(anyString(), anyString(), any(Object.class),
                any(CorrelationData.class));
    }

    private void confirmWith(boolean ack) {
        doAnswer(invocation -> {
            CorrelationData correlation = invocation.getArgument(3);
            correlation.getFuture().complete(new CorrelationData.Confirm(ack, ack ? null : "nack"));
            return null;
        }).when(rabbitTemplate).convertAndSend(anyString(), anyString(), any(Object.class), any(CorrelationData.class));
    }
}
