package com.codearena.api.judging;

import com.codearena.common.JudgeJob;
import com.codearena.common.JudgeQueues;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class JudgeJobPublisher {

    private static final Logger log = LoggerFactory.getLogger(JudgeJobPublisher.class);
    private static final String DEFAULT_EXCHANGE = "";

    private final JudgeJobFactory jobFactory;
    private final RabbitTemplate rabbitTemplate;
    private final MessagingProperties properties;

    public JudgeJobPublisher(JudgeJobFactory jobFactory, RabbitTemplate rabbitTemplate,
                             MessagingProperties properties) {
        this.jobFactory = jobFactory;
        this.rabbitTemplate = rabbitTemplate;
        this.properties = properties;
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onSubmissionCreated(SubmissionCreatedEvent event) {
        publish(event.submissionId());
    }

    public boolean publish(long submissionId) {
        try {
            Optional<JudgeJob> job = jobFactory.build(submissionId);
            if (job.isEmpty()) {
                log.warn("Submission {} or its problem no longer exists; no judge job published", submissionId);
                return false;
            }
            CorrelationData correlation = new CorrelationData("submission-" + submissionId);
            rabbitTemplate.convertAndSend(DEFAULT_EXCHANGE, JudgeQueues.JOBS, job.get(), correlation);
            CorrelationData.Confirm confirm = correlation.getFuture()
                    .get(properties.confirmTimeout().toMillis(), TimeUnit.MILLISECONDS);
            if (!confirm.isAck() || correlation.getReturned() != null) {
                log.warn("Broker did not accept judge job for submission {}; it stays PENDING", submissionId);
                return false;
            }
            log.info("Published judge job for submission {} ({} test cases)", submissionId,
                    job.get().testCases().size());
            return true;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            log.warn("Interrupted while publishing judge job for submission {}; it stays PENDING", submissionId);
            return false;
        } catch (Exception ex) {
            log.warn("Failed to publish judge job for submission {}; it stays PENDING: {}", submissionId, ex.toString());
            return false;
        }
    }
}
