package com.codearena.worker.messaging;

import com.codearena.common.JudgeQueues;
import com.codearena.worker.judge.JudgeService;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(MessagingProperties.class)
public class WorkerAmqpConfig {

    static {
        // Message.toString() is used by Spring AMQP's own log lines; this keeps payloads (source, test data) out.
        Message.setMaxBodyLength(0);
    }

    @Bean
    public Declarables judgeQueues() {
        return new Declarables(
                QueueBuilder.durable(JudgeQueues.JOBS).withArguments(JudgeQueues.JOBS_ARGUMENTS).build(),
                QueueBuilder.durable(JudgeQueues.RESULTS).withArguments(JudgeQueues.RESULTS_ARGUMENTS).build(),
                QueueBuilder.durable(JudgeQueues.JOBS_DLQ).withArguments(JudgeQueues.JOBS_DLQ_ARGUMENTS).build());
    }

    @Bean
    public MessageConverter judgeMessageConverter(ObjectMapper objectMapper) {
        return new Jackson2JsonMessageConverter(objectMapper, JudgeQueues.TRUSTED_PACKAGE);
    }

    @Bean
    public ResultPublisher resultPublisher(RabbitTemplate rabbitTemplate, MessagingProperties properties) {
        return new ResultPublisher(rabbitTemplate, properties.confirmTimeout());
    }

    @Bean
    public JudgeMetrics judgeMetrics(MeterRegistry registry) {
        return new JudgeMetrics(registry);
    }

    @Bean
    public JobProcessor jobProcessor(MessageConverter judgeMessageConverter, JudgeService judgeService,
                                     ResultPublisher resultPublisher, JudgeMetrics judgeMetrics) {
        return new JobProcessor(judgeMessageConverter, judgeService, resultPublisher, judgeMetrics);
    }
}
