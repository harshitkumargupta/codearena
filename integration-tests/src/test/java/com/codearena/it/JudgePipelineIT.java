package com.codearena.it;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

/**
 * Register, submit and receive verdicts through the whole system: api, RabbitMQ, worker, real Docker sandbox, result
 * queue and back into Postgres.
 */
@ExtendWith(CodeArenaStackExtension.class)
class JudgePipelineIT {

    private static ApiClient client;

    @BeforeAll
    static void startStack(CodeArenaStack stack) {
        client = new ApiClient(stack.baseUrl());
    }

    @Test
    void javaSolutionIsAcceptedAndWrongPythonSolutionIsRejected() {
        ApiClient.User user = client.register("pipe");
        long twoSum = client.problemId("two-sum");

        long accepted = client.submit(user, twoSum, "JAVA", Solutions.TWO_SUM_JAVA, null);
        long wrong = client.submit(user, twoSum, "PYTHON", Solutions.TWO_SUM_PYTHON_WRONG, null);

        JsonNode java = client.awaitVerdict(user, accepted);
        assertThat(java.get("status").asText()).as("java verdict: %s", java.get("verdictDetail")).isEqualTo("ACCEPTED");
        assertThat(java.get("timeMs").asInt()).isPositive();
        assertThat(java.get("memoryKb").asInt()).isPositive();

        JsonNode python = client.awaitVerdict(user, wrong);
        assertThat(python.get("status").asText()).isEqualTo("WRONG_ANSWER");
        assertThat(python.get("failedTestOrdinal").asInt()).isEqualTo(1);
    }
}
