package com.codearena.worker.messaging;

import com.codearena.common.JudgeQueues;
import com.rabbitmq.client.Channel;
import java.io.IOException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
public class JudgeJobListener {

    private final JobProcessor jobProcessor;

    public JudgeJobListener(JobProcessor jobProcessor) {
        this.jobProcessor = jobProcessor;
    }

    @RabbitListener(queues = JudgeQueues.JOBS, ackMode = "MANUAL")
    public void onJob(Message message, Channel channel) throws IOException {
        jobProcessor.process(message, channel);
    }
}
