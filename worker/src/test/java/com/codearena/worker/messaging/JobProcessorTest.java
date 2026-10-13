package com.codearena.worker.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.codearena.common.JudgeJob;
import com.codearena.common.JudgeResult;
import com.codearena.common.Language;
import com.codearena.common.Verdict;
import com.codearena.worker.judge.JudgeService;
import com.codearena.worker.testsupport.LogCapture;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.Channel;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.InOrder;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;

class JobProcessorTest {

    private static final long TAG = 42L;
    private static final String SOURCE = "print('SECRET_SOURCE_1d4e')";
    private static final String HIDDEN_INPUT = "HIDDEN_INPUT_7f3a";
    private static final String HIDDEN_EXPECTED = "HIDDEN_EXPECTED_9b2c";
    private static final JudgeJob JOB = new JudgeJob(7L, 3L, Language.PYTHON, SOURCE, 1000, 256,
            List.of(new JudgeJob.TestCase(1, HIDDEN_INPUT, HIDDEN_EXPECTED)));

    private final Jackson2JsonMessageConverter converter =
            new Jackson2JsonMessageConverter(new ObjectMapper(), "com.codearena.common");
    private final JudgeService judgeService = mock(JudgeService.class);
    private final ResultPublisher publisher = mock(ResultPublisher.class);
    private final Channel channel = mock(Channel.class);
    private final JobProcessor processor = new JobProcessor(converter, judgeService, publisher);

    @Test
    void acksOnlyAfterFinalResultIsPublished() throws Exception {
        JudgeResult accepted = new JudgeResult(7L, Verdict.ACCEPTED, null, 40, 5000, null);
        when(judgeService.judge(JOB)).thenReturn(accepted);

        processor.process(jobMessage(JOB), channel);

        InOrder order = inOrder(publisher, judgeService, channel);
        order.verify(publisher).publish(JudgeResult.running(7L));
        order.verify(judgeService).judge(JOB);
        order.verify(publisher).publish(accepted);
        order.verify(channel).basicAck(TAG, false);
        verify(channel, never()).basicNack(anyLong(), anyBoolean(), anyBoolean());
    }

    @ParameterizedTest
    @EnumSource(value = Verdict.class, names = {"WRONG_ANSWER", "TIME_LIMIT_EXCEEDED", "MEMORY_LIMIT_EXCEEDED",
            "RUNTIME_ERROR", "COMPILATION_ERROR"})
    void userCodeVerdictsArePublishedThenAcked(Verdict verdict) throws Exception {
        JudgeResult result = new JudgeResult(7L, verdict, 1, 10, 100, null);
        when(judgeService.judge(JOB)).thenReturn(result);

        processor.process(jobMessage(JOB), channel);

        InOrder order = inOrder(publisher, channel);
        order.verify(publisher).publish(result);
        order.verify(channel).basicAck(TAG, false);
    }

    @Test
    void failedResultPublishIsRequeuedAndNeverAcked() throws Exception {
        JudgeResult accepted = new JudgeResult(7L, Verdict.ACCEPTED, null, 40, 5000, null);
        when(judgeService.judge(JOB)).thenReturn(accepted);
        doThrow(new ResultPublishException("Broker nacked ACCEPTED for submission 7")).when(publisher).publish(accepted);

        processor.process(jobMessage(JOB), channel);

        verify(channel).basicNack(TAG, false, true);
        verify(channel, never()).basicAck(anyLong(), anyBoolean());
    }

    @Test
    void judgeErrorIsReturnedToQueueWithoutPublishingFinalResult() throws Exception {
        when(judgeService.judge(JOB)).thenReturn(
                new JudgeResult(7L, Verdict.INTERNAL_ERROR, null, null, null, "Internal judge error"));

        processor.process(jobMessage(JOB), channel);

        verify(publisher).publish(JudgeResult.running(7L));
        verify(publisher, never()).publish(org.mockito.ArgumentMatchers.argThat(r -> r.verdict() != Verdict.RUNNING));
        verify(channel).basicNack(TAG, false, true);
        verify(channel, never()).basicAck(anyLong(), anyBoolean());
    }

    @Test
    void runningPublishFailureDoesNotStopJudging() throws Exception {
        JudgeResult accepted = new JudgeResult(7L, Verdict.ACCEPTED, null, 40, 5000, null);
        doThrow(new ResultPublishException("down")).when(publisher).publish(JudgeResult.running(7L));
        when(judgeService.judge(JOB)).thenReturn(accepted);

        processor.process(jobMessage(JOB), channel);

        verify(publisher).publish(accepted);
        verify(channel).basicAck(TAG, false);
    }

    @Test
    void unexpectedJudgeExceptionIsRequeued() throws Exception {
        when(judgeService.judge(JOB)).thenThrow(new IllegalStateException("boom"));

        processor.process(jobMessage(JOB), channel);

        verify(channel).basicNack(TAG, false, true);
        verify(channel, never()).basicAck(anyLong(), anyBoolean());
    }

    @Test
    void unreadableMessageIsRejectedToDeadLetterQueue() throws Exception {
        Message garbage = new Message("{not json".getBytes(StandardCharsets.UTF_8), properties(Map.of(
                "__TypeId__", JudgeJob.class.getName())));

        processor.process(garbage, channel);

        verify(channel).basicReject(TAG, false);
        verifyNoInteractions(judgeService, publisher);
    }

    @Test
    void untrustedTypeIsRejected() throws Exception {
        Message untrusted = new Message("{\"a\":1}".getBytes(StandardCharsets.UTF_8), properties(Map.of(
                "__TypeId__", "java.util.HashMap")));

        processor.process(untrusted, channel);

        verify(channel).basicReject(TAG, false);
        verifyNoInteractions(judgeService);
    }

    @Test
    void payloadNeverAppearsInLogs() throws Exception {
        when(judgeService.judge(any())).thenThrow(new IllegalStateException("boom"));
        Message garbage = new Message(("{\"submissionId\":\"" + HIDDEN_INPUT).getBytes(StandardCharsets.UTF_8),
                properties(Map.of("__TypeId__", JudgeJob.class.getName())));

        try (LogCapture logs = new LogCapture()) {
            processor.process(jobMessage(JOB), channel);
            processor.process(garbage, channel);

            assertThat(logs.size()).isPositive();
            assertThat(logs.text()).doesNotContain("SECRET_SOURCE", HIDDEN_INPUT, HIDDEN_EXPECTED);
        }
    }

    @Test
    void springMessageToStringNeverIncludesBody() {
        Message message = jobMessage(JOB);
        new WorkerAmqpConfig();

        assertThat(message.toString()).doesNotContain("SECRET_SOURCE", HIDDEN_INPUT, HIDDEN_EXPECTED);
    }

    private Message jobMessage(JudgeJob job) {
        MessageProperties properties = properties(Map.of("x-delivery-count", 1L));
        Message message = converter.toMessage(job, properties);
        message.getMessageProperties().setDeliveryTag(TAG);
        return message;
    }

    private static MessageProperties properties(Map<String, Object> headers) {
        MessageProperties properties = new MessageProperties();
        properties.setDeliveryTag(TAG);
        properties.setContentType(MessageProperties.CONTENT_TYPE_JSON);
        headers.forEach(properties::setHeader);
        return properties;
    }
}
