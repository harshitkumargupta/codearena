package com.codearena.api.judging;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.codearena.common.JudgeJob;
import com.codearena.common.JudgeResult;
import com.codearena.common.Language;
import com.codearena.common.Verdict;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;

class JudgingListenersTest {

    private final Jackson2JsonMessageConverter converter =
            new Jackson2JsonMessageConverter(new ObjectMapper(), "com.codearena.common");
    private final SubmissionResultService resultService = mock(SubmissionResultService.class);
    private final JudgeResultListener resultListener = new JudgeResultListener(converter, resultService);
    private final DeadLetterListener deadLetterListener = new DeadLetterListener(converter, resultService);

    @Test
    void resultMessageIsApplied() {
        JudgeResult result = new JudgeResult(9L, Verdict.ACCEPTED, null, 40, 4000, null);

        resultListener.onResult(converter.toMessage(result, new MessageProperties()));

        verify(resultService).apply(result);
    }

    @Test
    void unreadableResultIsDropped() {
        resultListener.onResult(garbage());

        verifyNoInteractions(resultService);
    }

    @Test
    void deadLetteredJobMarksSubmissionFailed() {
        MessageProperties properties = new MessageProperties();
        properties.setHeader("x-first-death-reason", "delivery_limit");
        JudgeJob job = new JudgeJob(9L, 5L, Language.JAVA, "class Main {}", 1000, 256,
                List.of(new JudgeJob.TestCase(1, "in", "out")));

        deadLetterListener.onDeadLetter(converter.toMessage(job, properties));

        verify(resultService).markJudgingFailed(9L);
    }

    @Test
    void unreadableDeadLetterIsDropped() {
        deadLetterListener.onDeadLetter(garbage());

        verifyNoInteractions(resultService);
    }

    private static Message garbage() {
        MessageProperties properties = new MessageProperties();
        properties.setContentType(MessageProperties.CONTENT_TYPE_JSON);
        properties.setHeader("__TypeId__", "java.util.HashMap");
        return new Message("{\"x\":1}".getBytes(StandardCharsets.UTF_8), properties);
    }
}
