package com.codearena.worker.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.codearena.common.JudgeJob;
import com.codearena.common.JudgeQueues;
import com.codearena.common.JudgeResult;
import com.codearena.common.Language;
import com.codearena.common.Verdict;
import com.codearena.worker.judge.JudgeService;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Real RabbitMQ (Testcontainers), real topology and real JobProcessor; only JudgeService is mocked.
 * Pulled forward from Phase 6 to prove the quorum delivery limit and dead-lettering.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class JudgePipelineRabbitTest {

    private static final long RECEIVE_TIMEOUT_MS = 20_000;
    private static final RabbitMQContainer RABBIT =
            new RabbitMQContainer(DockerImageName.parse("rabbitmq:3.13-management"));

    @MockitoBean
    private JudgeService judgeService;

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Autowired
    private AmqpAdmin amqpAdmin;

    @Autowired
    private MessageConverter converter;

    @BeforeAll
    static void startRabbit() {
        if (!DockerClientFactory.instance().isDockerAvailable()) {
            String reason = "Docker is not available for Testcontainers. Start Docker, or set DOCKER_HOST "
                    + "(and TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=/var/run/docker.sock on Colima)";
            if ("true".equalsIgnoreCase(System.getenv("CI_REQUIRE_DOCKER"))) {
                throw new IllegalStateException(reason + " (CI_REQUIRE_DOCKER=true, so this fails instead of skipping)");
            }
            System.err.println("[rabbit pipeline test skipped] " + reason);
            Assumptions.abort(reason);
        }
        RABBIT.start();
    }

    @AfterAll
    static void stopRabbit() {
        if (RABBIT.isRunning()) {
            RABBIT.stop();
        }
    }

    @DynamicPropertySource
    static void rabbitProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.rabbitmq.host", RABBIT::getHost);
        registry.add("spring.rabbitmq.port", RABBIT::getAmqpPort);
        registry.add("spring.rabbitmq.username", RABBIT::getAdminUsername);
        registry.add("spring.rabbitmq.password", RABBIT::getAdminPassword);
    }

    @Test
    void judgeErrorsAreRetriedThreeTimesThenDeadLettered() {
        when(judgeService.judge(argThat(job -> job.submissionId() == 101L)))
                .thenReturn(new JudgeResult(101L, Verdict.INTERNAL_ERROR, null, null, null, "Internal judge error"));

        rabbitTemplate.convertAndSend("", JudgeQueues.JOBS, job(101L));

        Message dead = rabbitTemplate.receive(JudgeQueues.JOBS_DLQ, RECEIVE_TIMEOUT_MS);
        assertThat(dead).as("dead-lettered job").isNotNull();
        assertThat(dead.getMessageProperties().<Object>getHeader("x-first-death-reason")).isEqualTo("delivery_limit");
        assertThat(converter.fromMessage(dead)).isInstanceOfSatisfying(JudgeJob.class,
                job -> assertThat(job.submissionId()).isEqualTo(101L));
        verify(judgeService, times(JudgeQueues.MAX_ATTEMPTS)).judge(argThat(job -> job.submissionId() == 101L));

        List<JudgeResult> results = drainResults(101L);
        assertThat(results).hasSize(JudgeQueues.MAX_ATTEMPTS)
                .allSatisfy(result -> assertThat(result.verdict()).isEqualTo(Verdict.RUNNING));
        assertThat(messageCount(JudgeQueues.JOBS)).isZero();
    }

    @Test
    void userVerdictIsPublishedAndAckedExactlyOnce() {
        JudgeResult wrongAnswer = new JudgeResult(202L, Verdict.WRONG_ANSWER, 2, 35, 9000, null);
        when(judgeService.judge(argThat(job -> job.submissionId() == 202L))).thenReturn(wrongAnswer);

        rabbitTemplate.convertAndSend("", JudgeQueues.JOBS, job(202L));

        verify(judgeService, timeout(RECEIVE_TIMEOUT_MS)).judge(argThat(job -> job.submissionId() == 202L));
        List<JudgeResult> results = drainResults(202L);
        assertThat(results).containsExactly(JudgeResult.running(202L), wrongAnswer);
        assertThat(messageCount(JudgeQueues.JOBS)).isZero();
        assertThat(rabbitTemplate.receive(JudgeQueues.JOBS_DLQ, 500)).isNull();
        verify(judgeService, times(1)).judge(argThat(job -> job.submissionId() == 202L));
    }

    private List<JudgeResult> drainResults(long submissionId) {
        List<JudgeResult> results = new ArrayList<>();
        Message message;
        while ((message = rabbitTemplate.receive(JudgeQueues.RESULTS, 2_000)) != null) {
            JudgeResult result = (JudgeResult) converter.fromMessage(message);
            if (result.submissionId() == submissionId) {
                results.add(result);
            }
        }
        return results;
    }

    private int messageCount(String queue) {
        Properties properties = amqpAdmin.getQueueProperties(queue);
        return properties == null ? -1 : ((Number) properties.get(RabbitAdmin.QUEUE_MESSAGE_COUNT)).intValue();
    }

    private static JudgeJob job(long submissionId) {
        return new JudgeJob(submissionId, 1L, Language.PYTHON, "print(1)", 1000, 128,
                List.of(new JudgeJob.TestCase(1, "", "1")));
    }
}
