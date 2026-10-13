package com.codearena.worker.messaging;

import com.codearena.common.JudgeQueues;
import com.codearena.common.JudgeResult;
import java.time.Duration;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

public class ResultPublisher {

    private static final String DEFAULT_EXCHANGE = "";

    private final RabbitTemplate rabbitTemplate;
    private final Duration confirmTimeout;

    public ResultPublisher(RabbitTemplate rabbitTemplate, Duration confirmTimeout) {
        this.rabbitTemplate = rabbitTemplate;
        this.confirmTimeout = confirmTimeout;
    }

    /**
     * Publishes a persistent result and blocks until the broker confirms it.
     *
     * @throws ResultPublishException if the broker nacks, returns, or does not confirm in time
     */
    public void publish(JudgeResult result) {
        String what = result.verdict() + " for submission " + result.submissionId();
        CorrelationData correlation = new CorrelationData("result-" + result.submissionId() + "-" + result.verdict());
        try {
            rabbitTemplate.convertAndSend(DEFAULT_EXCHANGE, JudgeQueues.RESULTS, result, correlation);
            CorrelationData.Confirm confirm = correlation.getFuture()
                    .get(confirmTimeout.toMillis(), TimeUnit.MILLISECONDS);
            if (!confirm.isAck()) {
                throw new ResultPublishException("Broker nacked " + what + ": " + confirm.getReason());
            }
            if (correlation.getReturned() != null) {
                throw new ResultPublishException("Broker could not route " + what);
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new ResultPublishException("Interrupted while publishing " + what, ex);
        } catch (ExecutionException | TimeoutException | AmqpException ex) {
            throw new ResultPublishException("Failed to publish " + what, ex);
        }
    }
}
