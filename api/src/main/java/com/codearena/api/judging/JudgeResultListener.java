package com.codearena.api.judging;

import com.codearena.common.JudgeQueues;
import com.codearena.common.JudgeResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.stereotype.Component;

@Component
public class JudgeResultListener {

    private static final Logger log = LoggerFactory.getLogger(JudgeResultListener.class);

    private final MessageConverter converter;
    private final SubmissionResultService resultService;

    public JudgeResultListener(MessageConverter converter, SubmissionResultService resultService) {
        this.converter = converter;
        this.resultService = resultService;
    }

    @RabbitListener(queues = JudgeQueues.RESULTS)
    public void onResult(Message message) {
        AmqpMessages.read(converter, message, JudgeResult.class).ifPresentOrElse(resultService::apply,
                () -> log.warn("Dropping unreadable message on {} ({})", JudgeQueues.RESULTS,
                        AmqpMessages.describe(message)));
    }
}
