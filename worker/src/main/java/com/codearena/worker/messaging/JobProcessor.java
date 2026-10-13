package com.codearena.worker.messaging;

import com.codearena.common.JudgeJob;
import com.codearena.common.JudgeQueues;
import com.codearena.common.JudgeResult;
import com.codearena.common.Verdict;
import com.codearena.worker.judge.JudgeService;
import com.rabbitmq.client.Channel;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.support.converter.MessageConverter;

/**
 * Handles one delivery from {@code judge.jobs} with manual acks. The job is acked only after its final result
 * is confirmed by the broker. Judge-side failures (INTERNAL_ERROR) and publish failures are returned to the queue,
 * so the quorum queue's delivery limit moves the job to the DLQ after {@link JudgeQueues#MAX_ATTEMPTS} attempts.
 */
public class JobProcessor {

    private static final Logger log = LoggerFactory.getLogger(JobProcessor.class);
    private static final String DELIVERY_COUNT_HEADER = "x-delivery-count";

    private final MessageConverter converter;
    private final JudgeService judgeService;
    private final ResultPublisher resultPublisher;

    public JobProcessor(MessageConverter converter, JudgeService judgeService, ResultPublisher resultPublisher) {
        this.converter = converter;
        this.judgeService = judgeService;
        this.resultPublisher = resultPublisher;
    }

    public void process(Message message, Channel channel) throws IOException {
        MessageProperties properties = message.getMessageProperties();
        long deliveryTag = properties.getDeliveryTag();

        JudgeJob job = readJob(message);
        if (job == null) {
            log.error("Rejecting unreadable message on {} (messageId={}, type={}); it goes to {}", JudgeQueues.JOBS,
                    properties.getMessageId(), properties.<Object>getHeader("__TypeId__"), JudgeQueues.JOBS_DLQ);
            channel.basicReject(deliveryTag, false);
            return;
        }

        long submissionId = job.submissionId();
        int attempt = attempt(properties);
        log.info("Judging submission {} (attempt {}/{})", submissionId, attempt, JudgeQueues.MAX_ATTEMPTS);
        try {
            publishRunning(submissionId);
            JudgeResult result = judgeService.judge(job);
            if (result.verdict() == Verdict.INTERNAL_ERROR) {
                log.warn("Judge error for submission {} on attempt {}/{}; returning job to the queue", submissionId,
                        attempt, JudgeQueues.MAX_ATTEMPTS);
                channel.basicNack(deliveryTag, false, true);
                return;
            }
            resultPublisher.publish(result);
            channel.basicAck(deliveryTag, false);
        } catch (RuntimeException ex) {
            log.error("Could not complete submission {} on attempt {}/{}; returning job to the queue", submissionId,
                    attempt, JudgeQueues.MAX_ATTEMPTS, ex);
            channel.basicNack(deliveryTag, false, true);
        }
    }

    private void publishRunning(long submissionId) {
        try {
            resultPublisher.publish(JudgeResult.running(submissionId));
        } catch (ResultPublishException ex) {
            log.warn("Could not publish RUNNING for submission {}; judging anyway: {}", submissionId, ex.getMessage());
        }
    }

    private JudgeJob readJob(Message message) {
        try {
            return converter.fromMessage(message) instanceof JudgeJob job ? job : null;
        } catch (RuntimeException ex) {
            return null;
        }
    }

    private static int attempt(MessageProperties properties) {
        Object count = properties.getHeader(DELIVERY_COUNT_HEADER);
        return count instanceof Number number ? number.intValue() + 1 : 1;
    }
}
