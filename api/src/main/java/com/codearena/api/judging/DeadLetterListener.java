package com.codearena.api.judging;

import com.codearena.common.JudgeJob;
import com.codearena.common.JudgeQueues;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.stereotype.Component;

@Component
public class DeadLetterListener {

    private static final Logger log = LoggerFactory.getLogger(DeadLetterListener.class);
    private static final String DEATH_REASON_HEADER = "x-first-death-reason";

    private final MessageConverter converter;
    private final SubmissionResultService resultService;

    public DeadLetterListener(MessageConverter converter, SubmissionResultService resultService) {
        this.converter = converter;
        this.resultService = resultService;
    }

    @RabbitListener(queues = JudgeQueues.JOBS_DLQ)
    public void onDeadLetter(Message message) {
        Optional<JudgeJob> job = AmqpMessages.read(converter, message, JudgeJob.class);
        if (job.isEmpty()) {
            log.error("Dropping unreadable message on {} ({})", JudgeQueues.JOBS_DLQ, AmqpMessages.describe(message));
            return;
        }
        log.warn("Judge job for submission {} was dead-lettered (reason: {})", job.get().submissionId(),
                message.getMessageProperties().<Object>getHeader(DEATH_REASON_HEADER));
        resultService.markJudgingFailed(job.get().submissionId());
    }
}
